package com.sam.assigment.api.dto;

import com.sam.assigment.domain.ActorType;
import jakarta.validation.constraints.NotNull;

public record LikePostRequest(
        @NotNull ActorType actorType,
        @NotNull Long actorId
) {
}
