package ch.hftm.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /blogs/{id}/comments}. The blog is taken from the path, not from the body. */
public record CommentRequest(
        @NotBlank(message = "Text must not be blank")
        @Size(max = 1000, message = "Text must not exceed 1000 characters")
        String text) {
}
