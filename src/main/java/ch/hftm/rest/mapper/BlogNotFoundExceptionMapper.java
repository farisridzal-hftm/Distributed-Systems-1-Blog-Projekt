package ch.hftm.rest.mapper;

import ch.hftm.exception.BlogNotFoundException;
import ch.hftm.rest.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BlogNotFoundExceptionMapper implements ExceptionMapper<BlogNotFoundException> {

    @Override
    public Response toResponse(BlogNotFoundException exception) {
        Log.debugf("Responding 404: %s", exception.getMessage());

        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(404, "Not Found", exception.getMessage()))
                .build();
    }
}
