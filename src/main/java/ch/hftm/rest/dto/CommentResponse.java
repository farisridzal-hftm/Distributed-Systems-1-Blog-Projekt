package ch.hftm.rest.dto;

import java.time.Instant;

/** Public representation of a comment, without the back reference to its blog. */
public record CommentResponse(Long id, String text, String author, Instant createdAt) {
}
