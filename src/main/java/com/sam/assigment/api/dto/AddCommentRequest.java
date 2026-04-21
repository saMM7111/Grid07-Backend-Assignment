package com.sam.assigment.api.dto;

import com.sam.assigment.domain.ActorType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotNull ActorType authorType,
        @NotNull Long authorId,
        @NotBlank @Size(max = 2_000) String content,
        @NotNull @Min(1) Integer depthLevel
) {
}
