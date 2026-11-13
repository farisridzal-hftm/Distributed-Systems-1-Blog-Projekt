package ch.hftm.rest.mapper;

import ch.hftm.rest.ErrorResponse;
import ch.hftm.security.CurrentUser;
import io.quarkus.logging.Log;
import io.quarkus.security.ForbiddenException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Gives a 403 the same body as every other error. Covers both a missing role
 * ({@code @RolesAllowed}) and a failed ownership check ({@code BlogAccessDeniedException}).
 * Logged as a warning: a logged-in user tried something they are not allowed to do.
 */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Inject
    CurrentUser currentUser;

    @Inject
    UriInfo uriInfo;

    @Override
    public Response toResponse(ForbiddenException exception) {
        String message = exception.getMessage() != null ? exception.getMessage() : "Missing required role";
        Log.warnf("Access denied for %s on %s: %s", currentUser.name(), uriInfo.getPath(), message);

        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse(403, "Forbidden", message))
                .build();
    }
}
