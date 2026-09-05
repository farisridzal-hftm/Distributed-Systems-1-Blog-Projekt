package ch.hftm.service;

import ch.hftm.exception.BlogNotFoundException;
import ch.hftm.exception.InvalidSortException;
import ch.hftm.model.Blog;
import ch.hftm.model.BlogComment;
import ch.hftm.repository.BlogCommentRepository;
import ch.hftm.repository.BlogRepository;
import java.util.List;
import java.util.Set;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BlogService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "title");

    @Inject
    BlogRepository blogRepository;

    @Inject
    BlogCommentRepository blogCommentRepository;

    public List<Blog> getBlogs() {
        return getBlogs(null, "id", "asc");
    }

    /**
     * Lists blogs, optionally narrowed by a case-insensitive search over title and content
     * and ordered by one of {@code id} or {@code title}.
     */
    public List<Blog> getBlogs(String search, String sort, String order) {
        Sort sorting = buildSort(sort, order);

        List<Blog> blogs;
        if (search == null || search.isBlank()) {
            blogs = blogRepository.listAll(sorting);
        } else {
            String pattern = "%" + search.strip().toLowerCase() + "%";
            blogs = blogRepository.list("lower(title) like ?1 or lower(content) like ?1", sorting, pattern);
        }

        Log.info("Returning " + blogs.size() + " blogs (search=" + search + ", sort=" + sort + ", order=" + order + ")");
        return blogs;
    }

    public Blog getBlog(Long blogId) {
        return blogRepository.findByIdOptional(blogId)
                .orElseThrow(() -> new BlogNotFoundException(blogId));
    }

    @Transactional
    public void addBlog(Blog blog) {
        Log.info("Adding blog " + blog.getTitle());
        blogRepository.persist(blog);
    }

    @Transactional
    public Blog updateBlog(Long blogId, Blog updated) {
        var blog = getBlog(blogId);

        Log.info("Updating blog " + blogId);
        blog.setTitle(updated.getTitle());
        blog.setContent(updated.getContent());
        return blog;
    }

    @Transactional
    public void deleteBlog(Long blogId) {
        var blog = getBlog(blogId);

        Log.info("Deleting blog " + blogId);
        blogRepository.delete(blog);
    }

    @Transactional
    public BlogComment addCommentToBlog(Long blogId, String text) {
        var blog = getBlog(blogId);

        Log.info("Adding comment to blog " + blog.getTitle());
        var comment = new BlogComment(text);
        blog.addComment(comment);
        blogCommentRepository.persist(comment);
        return comment;
    }

    public List<BlogComment> getCommentsForBlog(Long blogId) {
        // Fails fast with 404 instead of returning an empty list for a blog that does not exist.
        getBlog(blogId);

        var comments = blogCommentRepository.list("blog.id", blogId);
        Log.info("Returning " + comments.size() + " comments for blog " + blogId);
        return comments;
    }

    private Sort buildSort(String sort, String order) {
        String field = (sort == null || sort.isBlank()) ? "id" : sort.strip().toLowerCase();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new InvalidSortException("Unsupported sort field '" + sort + "', allowed values are " + SORTABLE_FIELDS);
        }

        String direction = (order == null || order.isBlank()) ? "asc" : order.strip().toLowerCase();
        return switch (direction) {
            case "asc" -> Sort.by(field, Sort.Direction.Ascending);
            case "desc" -> Sort.by(field, Sort.Direction.Descending);
            default -> throw new InvalidSortException("Unsupported order '" + order + "', allowed values are [asc, desc]");
        };
    }
}
