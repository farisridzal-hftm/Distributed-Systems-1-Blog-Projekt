package ch.hftm.rest;

import ch.hftm.model.Blog;
import ch.hftm.rest.dto.BlogMapper;
import ch.hftm.rest.dto.BlogRequest;
import ch.hftm.rest.dto.BlogResponse;
import ch.hftm.rest.dto.CommentRequest;
import ch.hftm.rest.dto.CommentResponse;
import ch.hftm.security.Roles;
import ch.hftm.service.BlogService;
import java.util.List;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * REST interface for blogs and their comments. Requests and responses use the DTOs from
 * {@link ch.hftm.rest.dto}; the JPA entities never leave this class.
 *
 * <p>Reading is public, writing needs a Keycloak token with the matching role. Whether the
 * caller owns the blog is checked in {@link BlogService}, a role alone is not enough.
 */
@Path("/blogs")
@Produces(MediaType.APPLICATION_JSON)
public class BlogResource {

    @Inject
    BlogService blogService;

    @Inject
    BlogMapper blogMapper;

    @Inject
    UriInfo uriInfo;

    /**
     * Lists blogs. {@code search} narrows the result to blogs whose title or content contains the
     * term, {@code sort}/{@code order} decide the ordering and the optional {@code X-Max-Results}
     * header caps the number of returned entries.
     */
    @PermitAll
    @GET
    public List<BlogResponse> getBlogs(
            @QueryParam("search") String search,
            @QueryParam("sort") @DefaultValue("id") String sort,
            @QueryParam("order") @DefaultValue("asc") String order,
            @HeaderParam("X-Max-Results") @Positive(message = "X-Max-Results must be a positive number") Integer maxResults) {

        List<Blog> blogs = blogService.getBlogs(search, sort, order);

        if (maxResults != null && maxResults < blogs.size()) {
            blogs = blogs.subList(0, maxResults);
        }
        return blogs.stream().map(blogMapper::toResponse).toList();
    }

    @PermitAll
    @GET
    @Path("/{id}")
    public BlogResponse getBlog(@PathParam("id") Long id) {
        return blogMapper.toResponse(blogService.getBlog(id));
    }

    @RolesAllowed(Roles.AUTHOR)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createBlog(@Valid BlogRequest request) {
        Blog blog = blogMapper.toEntity(request);
        blogService.addBlog(blog);

        var location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(blog.getId())).build();
        return Response.created(location).entity(blogMapper.toResponse(blog)).build();
    }

    @RolesAllowed(Roles.AUTHOR)
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public BlogResponse updateBlog(@PathParam("id") Long id, @Valid BlogRequest request) {
        return blogMapper.toResponse(blogService.updateBlog(id, blogMapper.toEntity(request)));
    }

    @RolesAllowed({Roles.AUTHOR, Roles.ADMIN})
    @DELETE
    @Path("/{id}")
    public Response deleteBlog(@PathParam("id") Long id) {
        blogService.deleteBlog(id);
        return Response.noContent().build();
    }

    @PermitAll
    @GET
    @Path("/{id}/comments")
    public List<CommentResponse> getComments(@PathParam("id") Long id) {
        return blogService.getCommentsForBlog(id).stream().map(blogMapper::toResponse).toList();
    }

    @RolesAllowed(Roles.READER)
    @POST
    @Path("/{id}/comments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addComment(@PathParam("id") Long id, @Valid CommentRequest request) {
        var created = blogService.addCommentToBlog(id, request.text());

        var location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.getId())).build();
        return Response.created(location).entity(blogMapper.toResponse(created)).build();
    }
}
