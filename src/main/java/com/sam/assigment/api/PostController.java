package com.sam.assigment.api;

import com.sam.assigment.api.dto.AddCommentRequest;
import com.sam.assigment.api.dto.CommentResponse;
import com.sam.assigment.api.dto.CreatePostRequest;
import com.sam.assigment.api.dto.LikePostRequest;
import com.sam.assigment.api.dto.LikePostResponse;
import com.sam.assigment.api.dto.PostResponse;
import com.sam.assigment.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPost(@Valid @RequestBody CreatePostRequest request) {
        return postService.createPost(request);
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long postId, @Valid @RequestBody AddCommentRequest request) {
        return postService.addComment(postId, request);
    }

    @PostMapping("/{postId}/like")
    public LikePostResponse likePost(@PathVariable Long postId, @Valid @RequestBody LikePostRequest request) {
        return postService.likePost(postId, request);
    }
}
