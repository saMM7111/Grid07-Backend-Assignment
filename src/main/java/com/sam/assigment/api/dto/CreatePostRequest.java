package com.sam.assigment.api.dto;

import com.sam.assigment.domain.ActorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotNull ActorType authorType,
        @NotNull Long authorId,
        @NotBlank @Size(max = 5_000) String content
) {
}
