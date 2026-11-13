package ch.hftm;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Logs when the application starts and stops, together with the connections that matter when a
 * deployment does not behave as expected. Passwords are never part of this output.
 */
@ApplicationScoped
public class ApplicationLifecycle {

    void onStart(@Observes StartupEvent event) {
        Config config = ConfigProvider.getConfig();
        Log.infof("Blog API started: profiles=%s, database=%s, keycloak=%s",
                ConfigUtils.getProfiles(),
                valueOf(config, "quarkus.datasource.jdbc.url"),
                valueOf(config, "quarkus.oidc.auth-server-url"));
    }

    void onStop(@Observes ShutdownEvent event) {
        Log.info("Blog API is shutting down");
    }

    private static String valueOf(Config config, String property) {
        return config.getOptionalValue(property, String.class).orElse("<not configured>");
    }
}
