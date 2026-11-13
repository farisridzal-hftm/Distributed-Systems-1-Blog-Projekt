package ch.hftm.rest.mapper;

import ch.hftm.rest.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * JSON-B reports an unreadable request body as a plain {@link JsonbException}. It is the client's
 * mistake, so it becomes a 400 instead of reaching the catch-all for unexpected errors.
 */
@Provider
public class InvalidJsonExceptionMapper implements ExceptionMapper<JsonbException> {

    @Override
    public Response toResponse(JsonbException exception) {
        Log.debugf("Responding 400: unreadable JSON (%s)", exception.getMessage());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(400, "Bad Request", "Request body is not valid JSON"))
                .build();
    }
}
