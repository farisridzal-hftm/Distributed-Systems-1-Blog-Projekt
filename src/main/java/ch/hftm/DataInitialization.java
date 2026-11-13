package ch.hftm;

import ch.hftm.config.BlogConfig;
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

    // Test user "bob" from keycloak/blog-realm.json. The id is fixed in the realm file, so bob can
    // log in and edit the seed blogs after every fresh realm import.
    static final String SEED_AUTHOR_ID = "b4b8b584-8662-4d9e-80cb-419a80f1e492";
    static final String SEED_AUTHOR_NAME = "bob";

    @Inject
    BlogRepository blogRepository;

    @Inject
    BlogConfig config;

    @Transactional
    public void init(@Observes StartupEvent event) {
        if (!config.seedData().enabled()) {
            Log.info("Seed data is disabled (blog.seed-data.enabled=false)");
            return;
        }

        long existing = blogRepository.count();
        if (existing > 0) {
            Log.infof("Seed data skipped, the database already contains %d blogs", existing);
            return;
        }

        blogRepository.persist(seedBlog("First Blog", "This is my first blog"));
        blogRepository.persist(seedBlog("Second Blog", "This is my second blog"));
        Log.infof("Created 2 seed blogs owned by %s", SEED_AUTHOR_NAME);
    }

    private Blog seedBlog(String title, String content) {
        var blog = new Blog(title, content);
        blog.setAuthor(SEED_AUTHOR_ID, SEED_AUTHOR_NAME);
        return blog;
    }
}
