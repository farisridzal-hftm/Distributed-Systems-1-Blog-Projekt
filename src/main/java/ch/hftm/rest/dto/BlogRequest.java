package ch.hftm.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /blogs} and {@code PUT /blogs/{id}}. Only the fields a client may set are
 * part of it: the id is assigned by the server and can therefore not be injected through a request.
 */
public record BlogRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 120, message = "Title must not exceed 120 characters")
        String title,

        @NotBlank(message = "Content must not be blank")
        @Size(max = 4000, message = "Content must not exceed 4000 characters")
        String content) {
}
