package ch.hftm.rest.mapper;

import ch.hftm.rest.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Last line of defence for exceptions no other mapper handles. The full stack trace goes to the
 * log as an error, the client only gets a generic 500 without internals (API8).
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Exception> {

    @Inject
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
        // Exceptions that already carry their HTTP answer (unknown path, wrong method,
        // unsupported media type, ...) are client errors and keep their response.
        if (exception instanceof WebApplicationException webException) {
            Response response = webException.getResponse();
            Log.debugf("Responding %d: %s", response.getStatus(), exception.getMessage());
            return response;
        }

        Log.errorf(exception, "Unexpected error on %s", uriInfo.getPath());
        return Response.serverError()
                .entity(new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred"))
                .build();
    }
}
