package ch.hftm.repository;

import ch.hftm.model.BlogComment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BlogCommentRepository implements PanacheRepository<BlogComment> {
}
