package ch.hftm.rest.mapper;

import ch.hftm.rest.ErrorResponse;
import io.quarkus.logging.Log;
import io.quarkus.security.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Gives a 403 the same body as every other error. Covers both a missing role
 * ({@code @RolesAllowed}) and a failed ownership check ({@code BlogAccessDeniedException}).
 */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(ForbiddenException exception) {
        String message = exception.getMessage() != null ? exception.getMessage() : "Missing required role";
        Log.info("Responding 403: " + message);

        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse(403, "Forbidden", message))
                .build();
    }
}
