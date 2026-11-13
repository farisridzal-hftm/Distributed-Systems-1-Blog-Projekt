package ch.hftm.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Own configuration properties with the prefix {@code blog}. Like every Quarkus property they can
 * be overridden by environment variables, e.g. {@code BLOG_SEED_DATA_ENABLED=false}.
 */
@ConfigMapping(prefix = "blog")
public interface BlogConfig {

    SeedData seedData();

    interface SeedData {

        /** Create two demo blogs owned by "bob" when the database is empty. */
        @WithDefault("true")
        boolean enabled();
    }
}
