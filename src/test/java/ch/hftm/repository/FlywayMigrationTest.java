package ch.hftm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Flyway has migrated the test database to the newest version. That Quarkus started at all also
 * proves that Hibernate's schema validation accepts the migrated tables.
 */
@QuarkusTest
class FlywayMigrationTest {

    @Inject
    Flyway flyway;

    @Test
    void allMigrationsAreAppliedSuccessfully() {
        MigrationInfoService info = flyway.info();

        assertEquals("2", info.current().getVersion().getVersion());
        assertEquals(0, info.pending().length);
        assertTrue(Arrays.stream(info.applied()).allMatch(migration -> migration.getState() == MigrationState.SUCCESS));
    }

    @Test
    void appliedMigrationsMatchTheFilesInTheProject() {
        // Fails if an already applied migration file was edited afterwards (checksum mismatch).
        assertTrue(flyway.validateWithResult().validationSuccessful);
    }
}
