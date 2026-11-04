package ch.hftm.rest.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import ch.hftm.model.Blog;
import ch.hftm.model.BlogComment;
import org.junit.jupiter.api.Test;

/** Plain unit test: the mapper has no dependencies, so it runs without Quarkus or a database. */
class BlogMapperTest {

    private final BlogMapper mapper = new BlogMapper();

    @Test
    void requestBecomesNewEntityWithoutIdAndAuthor() {
        Blog blog = mapper.toEntity(new BlogRequest("Title", "Content"));

        assertNull(blog.getId());
        assertNull(blog.getAuthorId());
        assertEquals("Title", blog.getTitle());
        assertEquals("Content", blog.getContent());
    }

    @Test
    void blogIsMappedToResponseWithAuthorNameOnly() {
        Blog blog = new Blog("Title", "Content");
        blog.setAuthor("internal-keycloak-id", "bob");
        blog.addComment(new BlogComment("Not exposed"));

        assertEquals(new BlogResponse(null, "Title", "Content", "bob"), mapper.toResponse(blog));
    }

    @Test
    void commentIsMappedToResponse() {
        BlogComment comment = new BlogComment("Nice");
        comment.setAuthor("internal-keycloak-id", "alice");

        assertEquals(new CommentResponse(null, "Nice", "alice"), mapper.toResponse(comment));
    }
}
