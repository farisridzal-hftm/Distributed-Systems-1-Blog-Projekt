package ch.hftm.rest;

import java.util.List;

/** Uniform error body returned by every error mapper of this API. */
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private List<Violation> violations;

    public ErrorResponse() {
    }

    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public ErrorResponse(int status, String error, String message, List<Violation> violations) {
        this(status, error, message);
        this.violations = violations;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    /** A single failed Bean Validation constraint. */
    public static class Violation {

        private String field;
        private String message;

        public Violation() {
        }

        public Violation(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
