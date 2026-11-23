package ch.hftm.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class Blog {
    @Id
    @GeneratedValue
    private Long id;

    @NotBlank(message = "Title must not be blank")
    @Size(max = 120, message = "Title must not exceed 120 characters")
    private String title;

    @NotBlank(message = "Content must not be blank")
    @Size(max = 4000, message = "Content must not exceed 4000 characters")
    @Column(length = 4000)
    private String content;

    /** Keycloak user id (claim {@code sub}) of the author; decides who may change the blog. */
    private String authorId;

    /** Login name of the author at the time of writing, for display only. */
    private String authorName;

    /** Set by Hibernate when the blog is first stored; column added by migration V2. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "blog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BlogComment> comments = new ArrayList<>();

    public Blog() {
    }

    public Blog(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthor(String authorId, String authorName) {
        this.authorId = authorId;
        this.authorName = authorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<BlogComment> getComments() {
        return comments;
    }

    public void addComment(BlogComment comment) {
        comment.setBlog(this);
        comments.add(comment);
    }
}
