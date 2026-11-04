package ch.hftm.exception;

import io.quarkus.security.ForbiddenException;

/**
 * Thrown when a logged-in user has the right role but acts on a blog they do not own.
 * Extends Quarkus' {@link ForbiddenException}, so it ends up as HTTP 403 like a missing role.
 */
public class BlogAccessDeniedException extends ForbiddenException {

    public BlogAccessDeniedException(String message) {
        super(message);
    }
}
