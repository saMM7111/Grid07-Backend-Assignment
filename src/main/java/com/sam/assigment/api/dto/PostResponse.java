package com.sam.assigment.api.dto;

import com.sam.assigment.domain.ActorType;
import java.time.Instant;

public record PostResponse(
        Long id,
        Long authorId,
        ActorType authorType,
        String content,
        long likeCount,
        Instant createdAt
) {
}
