package com.financetracker.backend.migration;

import static org.assertj.core.api.Assertions.*;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = "app.database.migration-mode=validate")
class ReconciledSchemaApplicationTests {
    private static ReconciliationTestDatabase db;
    private static String preservedRecords;
    private static String preservedSequences;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;

    @DynamicPropertySource static void correctedLegacyDatabase(DynamicPropertyRegistry registry) throws Exception {
        db = new ReconciliationTestDatabase();
        try (var connection = db.connection()) {
            preservedRecords = ReconciliationTestDatabase.fingerprint(connection);
            preservedSequences = ReconciliationTestDatabase.sequences(connection);
        }
        db.reconcile();
        try (var connection = db.connection()) {
            DatabaseMigrationTool.baseline(connection, db.flyway(), db.database);
            connection.rollback();
        }
        registry.add("spring.datasource.url", () -> db.url);
        registry.add("spring.datasource.username", () -> db.user);
        registry.add("spring.datasource.password", () -> db.password);
    }

    @Test void correctedPopulatedSchemaStartsInValidationOnlyModeAndPreservesData() throws Exception {
        // Context startup invokes Hibernate validate against the reconciled, baselined legacy schema.
        assertThat(entityManagerFactory.isOpen()).isTrue();
        flyway.validate();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            BaselineSchemaVerifier.verify(connection, "public");
            assertThat(ReconciliationTestDatabase.fingerprint(connection)).isEqualTo(preservedRecords);
            assertThat(ReconciliationTestDatabase.sequences(connection)).isEqualTo(preservedSequences);
            assertThat(ReconciliationTestDatabase.scalar(connection,
                    "select count(*) from flyway_schema_history where version='1' and type='BASELINE' and success")).isEqualTo("1");
            connection.rollback();
        }
    }
}
