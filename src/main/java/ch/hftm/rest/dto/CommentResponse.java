package ch.hftm.rest.dto;

/** Public representation of a comment, without the back reference to its blog. */
public record CommentResponse(Long id, String text) {
}
