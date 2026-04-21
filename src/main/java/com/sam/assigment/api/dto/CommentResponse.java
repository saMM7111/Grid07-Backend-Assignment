package com.sam.assigment.api.dto;

import com.sam.assigment.domain.ActorType;
import java.time.Instant;

public record CommentResponse(
        Long id,
        Long postId,
        Long authorId,
        ActorType authorType,
        String content,
        int depthLevel,
        Instant createdAt
) {
}
