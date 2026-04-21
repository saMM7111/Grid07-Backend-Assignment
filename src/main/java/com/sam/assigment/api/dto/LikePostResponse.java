package com.sam.assigment.api.dto;

public record LikePostResponse(
        Long postId,
        long likeCount,
        String message
) {
}
