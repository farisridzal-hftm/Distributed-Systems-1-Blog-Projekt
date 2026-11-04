package ch.hftm.rest.dto;

/**
 * Public representation of a blog. Comments are not embedded, they are served under
 * {@code /blogs/{id}/comments}. {@code author} is the login name; the internal Keycloak id stays
 * on the server.
 */
public record BlogResponse(Long id, String title, String content, String author) {
}
