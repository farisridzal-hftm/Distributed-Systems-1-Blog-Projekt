package ch.hftm.rest.mapper;

import ch.hftm.exception.InvalidSortException;
import ch.hftm.rest.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidSortExceptionMapper implements ExceptionMapper<InvalidSortException> {

    @Override
    public Response toResponse(InvalidSortException exception) {
        Log.info("Responding 400: " + exception.getMessage());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(400, "Bad Request", exception.getMessage()))
                .build();
    }
}
