package ch.hftm.service;

import ch.hftm.model.Blog;
import ch.hftm.model.BlogComment;
import ch.hftm.repository.BlogCommentRepository;
import ch.hftm.repository.BlogRepository;
import java.util.List;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BlogService {
    @Inject
    BlogRepository blogRepository;

    @Inject
    BlogCommentRepository blogCommentRepository;

    public List<Blog> getBlogs() {
        var blogs = blogRepository.listAll();
        Log.info("Returning " + blogs.size() + " blogs");
        return blogs;
    }

    @Transactional
    public void addBlog(Blog blog) {
        Log.info("Adding blog " + blog.getTitle());
        blogRepository.persist(blog);
    }

    @Transactional
    public void addCommentToBlog(Long blogId, String text) {
        var blog = blogRepository.findByIdOptional(blogId)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found: " + blogId));

        Log.info("Adding comment to blog " + blog.getTitle());
        blog.addComment(new BlogComment(text));
    }

    public List<BlogComment> getCommentsForBlog(Long blogId) {
        var comments = blogCommentRepository.list("blog.id", blogId);
        Log.info("Returning " + comments.size() + " comments for blog " + blogId);
        return comments;
    }
}
