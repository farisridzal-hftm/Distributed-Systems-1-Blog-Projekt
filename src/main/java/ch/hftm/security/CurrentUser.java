package ch.hftm.security;

import ch.hftm.model.Blog;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * The logged-in user of the current request, read from the Keycloak access token. Ownership is
 * decided by the stable user id (claim {@code sub}); the login name is only stored for display.
 */
@RequestScoped
public class CurrentUser {

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken token;

    /** Keycloak user id (claim {@code sub}), {@code null} for anonymous requests. */
    public String id() {
        return token.getSubject();
    }

    /** Login name (claim {@code preferred_username}). */
    public String name() {
        return token.getClaim("preferred_username");
    }

    public boolean isAdmin() {
        return identity.hasRole(Roles.ADMIN);
    }

    /** A blog without author belongs to nobody, so an anonymous caller never owns anything. */
    public boolean isAuthorOf(Blog blog) {
        return id() != null && id().equals(blog.getAuthorId());
    }
}
