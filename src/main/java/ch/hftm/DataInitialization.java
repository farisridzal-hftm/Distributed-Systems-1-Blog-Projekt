package ch.hftm;

import ch.hftm.model.Blog;
import ch.hftm.repository.BlogRepository;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DataInitialization {

    @Inject
    BlogRepository blogRepository;

    @Transactional
    public void init(@Observes StartupEvent event) {
        if (blogRepository.count() == 0) {
            Log.info("Initializing blog test data");

            blogRepository.persist(new Blog("First Blog", "This is my first blog"));
            blogRepository.persist(new Blog("Second Blog", "This is my second blog"));
        }
    }
}