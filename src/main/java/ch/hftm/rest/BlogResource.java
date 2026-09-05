package ch.hftm.rest;

import ch.hftm.model.Blog;
import ch.hftm.model.BlogComment;
import ch.hftm.service.BlogService;
import java.util.List;
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

@Path("/blogs")
@Produces(MediaType.APPLICATION_JSON)
public class BlogResource {

    @Inject
    BlogService blogService;

    @Inject
    UriInfo uriInfo;

    /**
     * Lists blogs. {@code search} narrows the result to blogs whose title or content contains the
     * term, {@code sort}/{@code order} decide the ordering and the optional {@code X-Max-Results}
     * header caps the number of returned entries.
     */
    @GET
    public List<Blog> getBlogs(
            @QueryParam("search") String search,
            @QueryParam("sort") @DefaultValue("id") String sort,
            @QueryParam("order") @DefaultValue("asc") String order,
            @HeaderParam("X-Max-Results") @Positive(message = "X-Max-Results must be a positive number") Integer maxResults) {

        List<Blog> blogs = blogService.getBlogs(search, sort, order);

        if (maxResults != null && maxResults < blogs.size()) {
            return blogs.subList(0, maxResults);
        }
        return blogs;
    }

    @GET
    @Path("/{id}")
    public Blog getBlog(@PathParam("id") Long id) {
        return blogService.getBlog(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createBlog(@Valid Blog blog) {
        blogService.addBlog(blog);

        var location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(blog.getId())).build();
        return Response.created(location).entity(blog).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Blog updateBlog(@PathParam("id") Long id, @Valid Blog blog) {
        return blogService.updateBlog(id, blog);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteBlog(@PathParam("id") Long id) {
        blogService.deleteBlog(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/comments")
    public List<BlogComment> getComments(@PathParam("id") Long id) {
        return blogService.getCommentsForBlog(id);
    }

    @POST
    @Path("/{id}/comments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addComment(@PathParam("id") Long id, @Valid BlogComment comment) {
        var created = blogService.addCommentToBlog(id, comment.getText());

        var location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.getId())).build();
        return Response.created(location).entity(created).build();
    }
}
