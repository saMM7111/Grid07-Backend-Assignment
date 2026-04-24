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
import com.sam.assigment.domain.Bot;
import com.sam.assigment.domain.Comment;
import com.sam.assigment.domain.Post;
import com.sam.assigment.repository.CommentRepository;
import com.sam.assigment.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

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
        ActorType authorType = author.getActorType();
        String normalizedContent = normalizeText(request.content(), "Comment content cannot be empty");
        botReplyGuardrailService.enforceDepthCap(request.depthLevel());

        BotReplyGuardrailService.Reservation reservation = reserveBotReplyIfNeeded(postId, post, author);
        registerReservationRollbackOnTransactionFailure(reservation);

        Comment comment = new Comment(
                post,
                author,
                normalizedContent,
                request.depthLevel()
        );

        Comment saved = commentRepository.save(comment);
        BotNotificationContext notificationContext = buildBotNotificationContext(post, author, "replied to");

        registerAfterCommit(() -> {
            applyCommentViralityScore(postId, authorType);
            sendBotNotification(notificationContext);
        });

        return toCommentResponse(saved);
    }

    @Transactional
    public LikePostResponse likePost(Long postId, LikePostRequest request) {
        Actor actor = actorResolver.resolve(request.actorType(), request.actorId());
        ActorType actorType = actor.getActorType();

        Post post = findPost(postId);
        post.incrementLikeCount();

        BotNotificationContext notificationContext = buildBotNotificationContext(post, actor, "liked");
        registerAfterCommit(() -> applyLikeSideEffects(postId, actorType, notificationContext));

        return new LikePostResponse(post.getId(), post.getLikeCount(), "Post liked successfully");
    }

    private BotReplyGuardrailService.Reservation reserveBotReplyIfNeeded(Long postId, Post post, Actor author) {
        if (author.getActorType() != ActorType.BOT) {
            return null;
        }

        Long humanTargetId = resolveHumanTargetId(post);
        return botReplyGuardrailService.reserveBotReply(postId, author.getId(), humanTargetId);
    }

    private void registerReservationRollbackOnTransactionFailure(BotReplyGuardrailService.Reservation reservation) {
        if (reservation == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            rollbackReservationSafely(reservation);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    rollbackReservationSafely(reservation);
                }
            }
        });
    }

    private void rollbackReservationSafely(BotReplyGuardrailService.Reservation reservation) {
        try {
            botReplyGuardrailService.rollbackReservation(reservation);
        } catch (RuntimeException ex) {
            log.error("Failed to rollback Redis bot reservation for key {}", reservation.botCountKey(), ex);
        }
    }

    private void registerAfterCommit(Runnable sideEffectAction) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            runSideEffectSafely(sideEffectAction);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSideEffectSafely(sideEffectAction);
            }
        });
    }

    private void runSideEffectSafely(Runnable sideEffectAction) {
        try {
            sideEffectAction.run();
        } catch (RuntimeException ex) {
            log.error("Post-commit Redis side effect failed", ex);
        }
    }

    private void applyLikeSideEffects(Long postId, ActorType actorType, BotNotificationContext notificationContext) {
        if (actorType == ActorType.USER) {
            viralityScoreService.applyInteraction(postId, ViralityInteraction.HUMAN_LIKE);
            return;
        }

        sendBotNotification(notificationContext);
    }

    private BotNotificationContext buildBotNotificationContext(Post post, Actor actor, String interactionAction) {
        if (actor.getActorType() != ActorType.BOT) {
            return null;
        }

        Actor postAuthor = post.getAuthor();
        if (postAuthor.getActorType() != ActorType.USER) {
            return null;
        }

        String botName = actor instanceof Bot bot ? bot.getName() : null;
        return new BotNotificationContext(postAuthor.getId(), actor.getId(), botName, interactionAction);
    }

    private void sendBotNotification(BotNotificationContext notificationContext) {
        if (notificationContext == null) {
            return;
        }

        notificationEngineService.handleBotInteraction(
                notificationContext.userId(),
                notificationContext.botId(),
                notificationContext.botName(),
                notificationContext.interactionAction()
        );
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

    private record BotNotificationContext(Long userId, Long botId, String botName, String interactionAction) {
    }
}
