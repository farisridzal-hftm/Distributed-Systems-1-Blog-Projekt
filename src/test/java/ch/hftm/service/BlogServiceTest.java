package ch.hftm.service;

import ch.hftm.exception.BlogNotFoundException;
import ch.hftm.exception.InvalidSortException;
import ch.hftm.model.Blog;
import ch.hftm.model.BlogComment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void searchingBlogsIgnoresCaseAndMatchesContent() {
        // Arrange
        blogService.addBlog(new Blog("Quarkus Basics", "All about Panache"));
        blogService.addBlog(new Blog("Cooking", "Nothing technical here"));

        // Act
        List<Blog> byTitle = blogService.getBlogs("quarkus", "id", "asc");
        List<Blog> byContent = blogService.getBlogs("PANACHE", "id", "asc");

        // Assert
        assertEquals(1, byTitle.size());
        assertEquals("Quarkus Basics", byTitle.getFirst().getTitle());
        assertEquals(1, byContent.size());
    }

    @Test
    @TestTransaction
    void sortingBlogsByTitleDescending() {
        // Arrange
        blogService.addBlog(new Blog("AAA Sorting Blog", "First"));
        blogService.addBlog(new Blog("ZZZ Sorting Blog", "Last"));

        // Act
        List<Blog> blogs = blogService.getBlogs("Sorting Blog", "title", "desc");

        // Assert
        assertEquals(2, blogs.size());
        assertEquals("ZZZ Sorting Blog", blogs.getFirst().getTitle());
    }

    @Test
    void sortingByUnknownFieldIsRejected() {
        // Act & Assert
        assertThrows(InvalidSortException.class, () -> blogService.getBlogs(null, "password", "asc"));
    }

    @Test
    @TestTransaction
    void updatingBlog() {
        // Arrange
        Blog blog = new Blog("Old Title", "Old content");
        blogService.addBlog(blog);

        // Act
        Blog updated = blogService.updateBlog(blog.getId(), new Blog("New Title", "New content"));

        // Assert
        assertEquals("New Title", updated.getTitle());
        assertEquals("New content", blogService.getBlog(blog.getId()).getContent());
    }

    @Test
    @TestTransaction
    void deletingBlog() {
        // Arrange
        Blog blog = new Blog("Doomed Blog", "This will be deleted");
        blogService.addBlog(blog);

        // Act
        blogService.deleteBlog(blog.getId());

        // Assert
        assertThrows(BlogNotFoundException.class, () -> blogService.getBlog(blog.getId()));
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
        var exception = assertThrows(BlogNotFoundException.class, () -> blogService.addCommentToBlog(-1L, "No blog here"));
        assertTrue(exception.getMessage().contains("-1"));
    }
}
