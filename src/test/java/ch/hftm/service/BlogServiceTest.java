package ch.hftm.service;

import ch.hftm.exception.BlogAccessDeniedException;
import ch.hftm.exception.BlogNotFoundException;
import ch.hftm.exception.InvalidSortException;
import ch.hftm.model.Blog;
import ch.hftm.model.BlogComment;
import ch.hftm.repository.BlogRepository;
import ch.hftm.security.Roles;
import static ch.hftm.security.TestUsers.ADMIN;
import static ch.hftm.security.TestUsers.ADMIN_ID;
import static ch.hftm.security.TestUsers.BOB;
import static ch.hftm.security.TestUsers.BOB_ID;
import static ch.hftm.security.TestUsers.CAROL;
import static ch.hftm.security.TestUsers.CAROL_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;

/** Business logic, called directly. The logged-in user is "bob" unless a test says otherwise. */
@QuarkusTest
@TestSecurity(user = BOB, roles = {Roles.READER, Roles.AUTHOR})
@OidcSecurity(claims = @Claim(key = "sub", value = BOB_ID))
public class BlogServiceTest {
    @Inject
    BlogService blogService;

    @Inject
    BlogRepository blogRepository;

    /** Stores a blog written by carol, bypassing the service that would make bob the author. */
    private Blog blogOfCarol() {
        Blog blog = new Blog("Carol's Blog", "Written by carol");
        blog.setAuthor(CAROL_ID, CAROL);
        blogRepository.persist(blog);
        return blog;
    }

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

    @Test
    @TestTransaction
    void addingBlogMakesTheCurrentUserItsAuthor() {
        // Arrange
        Blog blog = new Blog("Bob's Blog", "Written by bob");

        // Act
        blogService.addBlog(blog);

        // Assert
        assertEquals(BOB_ID, blog.getAuthorId());
        assertEquals(BOB, blog.getAuthorName());
    }

    @Test
    @TestTransaction
    void updatingSomeoneElsesBlogIsDenied() {
        // Arrange
        Blog foreign = blogOfCarol();

        // Act & Assert
        assertThrows(BlogAccessDeniedException.class,
                () -> blogService.updateBlog(foreign.getId(), new Blog("Hijacked", "Not bob's blog")));
        assertEquals("Carol's Blog", blogService.getBlog(foreign.getId()).getTitle());
    }

    @Test
    @TestTransaction
    void deletingSomeoneElsesBlogIsDenied() {
        // Arrange
        Blog foreign = blogOfCarol();

        // Act & Assert
        assertThrows(BlogAccessDeniedException.class, () -> blogService.deleteBlog(foreign.getId()));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = ADMIN, roles = {Roles.READER, Roles.ADMIN})
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void adminMayDeleteSomeoneElsesBlog() {
        // Arrange
        Blog foreign = blogOfCarol();

        // Act
        blogService.deleteBlog(foreign.getId());

        // Assert
        assertThrows(BlogNotFoundException.class, () -> blogService.getBlog(foreign.getId()));
    }

    @Test
    @TestTransaction
    void commentGetsTheCurrentUserAsAuthor() {
        // Arrange
        Blog blog = blogOfCarol();

        // Act
        BlogComment comment = blogService.addCommentToBlog(blog.getId(), "Comment by bob");

        // Assert
        assertEquals(BOB_ID, comment.getAuthorId());
        assertEquals(BOB, comment.getAuthorName());
    }
}
