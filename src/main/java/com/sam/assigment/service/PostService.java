package com.sam.assigment.service;

import com.sam.assigment.api.dto.AddCommentRequest;
import com.sam.assigment.api.dto.CommentResponse;
import com.sam.assigment.api.dto.CreatePostRequest;
import com.sam.assigment.api.dto.LikePostRequest;
import com.sam.assigment.api.dto.LikePostResponse;
import com.sam.assigment.api.dto.PostResponse;
import com.sam.assigment.api.exception.BadRequestException;
import com.sam.assigment.api.exception.ResourceNotFoundException;
import com.sam.assigment.domain.Actor;
import com.sam.assigment.domain.ActorType;
import com.sam.assigment.domain.Comment;
import com.sam.assigment.domain.Post;
import com.sam.assigment.repository.CommentRepository;
import com.sam.assigment.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ActorResolver actorResolver;
    private final ViralityScoreService viralityScoreService;
    private final BotReplyGuardrailService botReplyGuardrailService;
    private final NotificationEngineService notificationEngineService;

    public PostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            ActorResolver actorResolver,
            ViralityScoreService viralityScoreService,
            BotReplyGuardrailService botReplyGuardrailService,
            NotificationEngineService notificationEngineService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.actorResolver = actorResolver;
        this.viralityScoreService = viralityScoreService;
        this.botReplyGuardrailService = botReplyGuardrailService;
        this.notificationEngineService = notificationEngineService;
    }

    @Transactional
    public PostResponse createPost(CreatePostRequest request) {
        Actor author = actorResolver.resolve(request.authorType(), request.authorId());

        Post post = new Post(author, normalizeText(request.content(), "Post content cannot be empty"));
        Post saved = postRepository.save(post);

        return toPostResponse(saved);
    }

    @Transactional
    public CommentResponse addComment(Long postId, AddCommentRequest request) {
        Post post = findPost(postId);
        Actor author = actorResolver.resolve(request.authorType(), request.authorId());
        botReplyGuardrailService.enforceDepthCap(request.depthLevel());

        BotReplyGuardrailService.Reservation reservation = null;
        boolean rollbackReservation = false;

        if (author.getActorType() == ActorType.BOT) {
            Long humanTargetId = resolveHumanTargetId(post);
            reservation = botReplyGuardrailService.reserveBotReply(postId, author.getId(), humanTargetId);
            rollbackReservation = true;
        }

        try {
            Comment comment = new Comment(
                    post,
                    author,
                    normalizeText(request.content(), "Comment content cannot be empty"),
                    request.depthLevel()
            );

            Comment saved = commentRepository.save(comment);
            applyCommentViralityScore(postId, author.getActorType());
            if (author.getActorType() == ActorType.BOT) {
                notificationEngineService.handleBotInteraction(post, author, "replied to");
            }
            rollbackReservation = false;
            return toCommentResponse(saved);
        } finally {
            if (rollbackReservation) {
                // Keep Redis counters consistent when DB write or scoring fails.
                botReplyGuardrailService.rollbackReservation(reservation);
            }
        }
    }

    @Transactional
    public LikePostResponse likePost(Long postId, LikePostRequest request) {
        Actor actor = actorResolver.resolve(request.actorType(), request.actorId());

        Post post = findPost(postId);
        post.incrementLikeCount();
        if (actor.getActorType() == ActorType.USER) {
            viralityScoreService.applyInteraction(postId, ViralityInteraction.HUMAN_LIKE);
        } else {
            notificationEngineService.handleBotInteraction(post, actor, "liked");
        }

        return new LikePostResponse(post.getId(), post.getLikeCount(), "Post liked successfully");
    }

    private void applyCommentViralityScore(Long postId, ActorType actorType) {
        if (actorType == ActorType.BOT) {
            viralityScoreService.applyInteraction(postId, ViralityInteraction.BOT_REPLY);
            return;
        }

        viralityScoreService.applyInteraction(postId, ViralityInteraction.HUMAN_COMMENT);
    }

    private Long resolveHumanTargetId(Post post) {
        return post.getAuthor().getActorType() == ActorType.USER ? post.getAuthor().getId() : null;
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
    }

    private String normalizeText(String text, String errorMessage) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            throw new BadRequestException(errorMessage);
        }
        return trimmed;
    }

    private PostResponse toPostResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getAuthor().getId(),
                post.getAuthor().getActorType(),
                post.getContent(),
                post.getLikeCount(),
                post.getCreatedAt()
        );
    }

    private CommentResponse toCommentResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getActorType(),
                comment.getContent(),
                comment.getDepthLevel(),
                comment.getCreatedAt()
        );
    }
}
