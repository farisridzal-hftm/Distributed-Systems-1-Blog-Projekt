package ch.hftm.repository;

import ch.hftm.model.Blog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class BlogRepositoryTest {
    @Inject
    BlogRepository blogRepository;

    @Test
    @TestTransaction
    void listingAndAddingBlogs() {
        // Arrange
        Blog blog = new Blog("Testing Blog", "This is my testing blog");
        int blogsBefore = blogRepository.listAll().size();

        // Act
        blogRepository.persist(blog);
        List<Blog> blogs = blogRepository.listAll();

        // Assert
        assertEquals(blogsBefore + 1, blogs.size());
    }
}
