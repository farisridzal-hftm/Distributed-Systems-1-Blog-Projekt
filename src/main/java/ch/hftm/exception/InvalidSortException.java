package ch.hftm.exception;

/** Thrown when a request asks for a sort field or direction that is not supported. Mapped to HTTP 400. */
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String message) {
        super(message);
    }
}
