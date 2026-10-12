package ch.hftm.rest.dto;

import ch.hftm.model.Blog;
import ch.hftm.model.BlogComment;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Translates between the JPA entities and the DTOs of the REST layer. Every field is copied
 * explicitly, so a new entity field only becomes visible in the API once it is added here.
 */
@ApplicationScoped
public class BlogMapper {

    public Blog toEntity(BlogRequest request) {
        return new Blog(request.title(), request.content());
    }

    public BlogResponse toResponse(Blog blog) {
        return new BlogResponse(blog.getId(), blog.getTitle(), blog.getContent());
    }

    public CommentResponse toResponse(BlogComment comment) {
        return new CommentResponse(comment.getId(), comment.getText());
    }
}
