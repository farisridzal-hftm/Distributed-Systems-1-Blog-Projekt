package ch.hftm.rest.mapper;

import ch.hftm.rest.ErrorResponse;
import ch.hftm.rest.ErrorResponse.Violation;
import java.util.List;
import io.quarkus.logging.Log;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Turns Bean Validation failures into a 400 that names every rejected field,
 * instead of the default Quarkus violation report.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<Violation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new Violation(fieldName(violation), violation.getMessage()))
                .toList();

        Log.info("Responding 400: " + violations.size() + " constraint violation(s)");

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(400, "Bad Request", "Request validation failed", violations))
                .build();
    }

    /** Reduces a property path like {@code createBlog.blog.title} to just {@code title}. */
    private String fieldName(ConstraintViolation<?> violation) {
        String last = null;
        for (Path.Node node : violation.getPropertyPath()) {
            last = node.getName();
        }
        return last;
    }
}
