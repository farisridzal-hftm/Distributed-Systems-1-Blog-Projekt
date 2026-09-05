package ch.hftm.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.bind.annotation.JsonbTransient;
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
    @Column(length = 4000)
    private String content;

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

    // Not part of the JSON representation: comments are served under /blogs/{id}/comments,
    // which also keeps the lazy relation out of the serializer. DTO mapping follows later.
    @JsonbTransient
    public List<BlogComment> getComments() {
        return comments;
    }

    public void addComment(BlogComment comment) {
        comment.setBlog(this);
        comments.add(comment);
    }
}
