package ch.hftm.rest.dto;

/**
 * Public representation of a blog. Comments are not embedded, they are served under
 * {@code /blogs/{id}/comments}.
 */
public record BlogResponse(Long id, String title, String content) {
}
