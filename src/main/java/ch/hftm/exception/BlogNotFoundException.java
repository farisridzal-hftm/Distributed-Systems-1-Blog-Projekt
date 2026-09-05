package ch.hftm.exception;

/** Thrown when a blog is addressed by an id that does not exist. Mapped to HTTP 404. */
public class BlogNotFoundException extends RuntimeException {

    public BlogNotFoundException(Long blogId) {
        super("Blog with id " + blogId + " not found");
    }
}
