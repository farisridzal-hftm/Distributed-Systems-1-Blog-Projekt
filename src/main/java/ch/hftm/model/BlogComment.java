package ch.hftm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class BlogComment {
    @Id
    @GeneratedValue
    private Long id;

    @NotBlank(message = "Text must not be blank")
    @Size(max = 1000, message = "Text must not exceed 1000 characters")
    @Column(length = 1000)
    private String text;

    /** Keycloak user id (claim {@code sub}) of the author. */
    private String authorId;

    /** Login name of the author at the time of writing, for display only. */
    private String authorName;

    @ManyToOne(fetch = FetchType.LAZY)
    private Blog blog;

    public BlogComment() {
    }

    public BlogComment(String text) {
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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

    public Blog getBlog() {
        return blog;
    }

    public void setBlog(Blog blog) {
        this.blog = blog;
    }
}
