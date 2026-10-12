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
    void requestBecomesNewEntityWithoutId() {
        Blog blog = mapper.toEntity(new BlogRequest("Title", "Content"));

        assertNull(blog.getId());
        assertEquals("Title", blog.getTitle());
        assertEquals("Content", blog.getContent());
    }

    @Test
    void blogIsMappedToResponseWithoutComments() {
        Blog blog = new Blog("Title", "Content");
        blog.addComment(new BlogComment("Not exposed"));

        assertEquals(new BlogResponse(null, "Title", "Content"), mapper.toResponse(blog));
    }

    @Test
    void commentIsMappedToResponse() {
        assertEquals(new CommentResponse(null, "Nice"), mapper.toResponse(new BlogComment("Nice")));
    }
}
