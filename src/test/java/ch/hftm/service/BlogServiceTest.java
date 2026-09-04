package ch.hftm.service;

import ch.hftm.model.Blog;
import ch.hftm.model.BlogComment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class BlogServiceTest {
    @Inject
    BlogService blogService;

    @Test
    @TestTransaction
    void listingAndAddingBlogs() {
        // Arrange
        Blog blog = new Blog("Testing Blog", "This is my testing blog");
        int blogsBefore = blogService.getBlogs().size();

        // Act
        blogService.addBlog(blog);
        List<Blog> blogs = blogService.getBlogs();

        // Assert
        assertEquals(blogsBefore + 1, blogs.size());
    }

    @Test
    @TestTransaction
    void addingCommentToBlog() {
        // Arrange
        Blog blog = new Blog("Blog with Comment", "This blog has a comment");
        blogService.addBlog(blog);

        // Act
        blogService.addCommentToBlog(blog.getId(), "This is my first comment");
        List<BlogComment> comments = blogService.getCommentsForBlog(blog.getId());

        // Assert
        assertEquals(1, comments.size());
        assertEquals("This is my first comment", comments.getFirst().getText());
    }

    @Test
    @TestTransaction
    void addingCommentToUnknownBlog() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> blogService.addCommentToBlog(-1L, "No blog here"));
    }
}
