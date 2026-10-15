package com.financetracker.backend.migration;

import static com.financetracker.backend.migration.ReconciliationTestDatabase.*;
import static org.assertj.core.api.Assertions.*;

import com.financetracker.backend.BackendApplication;
import com.financetracker.backend.config.InsightsAiProperties;
import com.financetracker.backend.config.MarketDataProperties;
import com.financetracker.backend.services.RecurringTransactionScheduler;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

/** Full backend startup, using only a newly created guarded disposable database. */
class BaselinedBackendStartupTests {
    @Test void repeatedFullStartupAcceptsExplicitV1BaselineWithReadOnlyConnectionsAndNoWriters() throws Exception {
        var db = new ReconciliationTestDatabase();
        db.reconcile();
        try (var connection = db.connection()) {
            DatabaseMigrationTool.baseline(connection, db.flyway(), db.database);
            connection.rollback();
        }
        try (var connection = db.connection()) { BaselineSchemaVerifier.verify(connection, "public"); }
        db.currentFlyway().migrate(); // Separate explicit setup; application startup is still validate-only.
        String before = snapshot(db);
        for (int attempt = 0; attempt < 2; attempt++) {
            DisposableDatabaseGuard.requireDisposable(db.url);
            var application = new SpringApplication(BackendApplication.class);
            try (var context = application.run(
                    "--spring.profiles.active=test",
                    "--spring.datasource.url=" + db.url,
                    "--spring.datasource.username=" + db.user,
                    "--spring.datasource.password=" + db.password,
                    // PostgreSQL enforces read-only for every application connection, including Flyway.
                    "--spring.datasource.hikari.data-source-properties.options=-c default_transaction_read_only=on",
                    "--spring.flyway.enabled=true",
                    "--spring.flyway.baseline-on-migrate=false",
                    "--spring.flyway.clean-disabled=true",
                    "--spring.flyway.create-schemas=false",
                    "--app.database.migration-mode=validate",
                    "--spring.jpa.hibernate.ddl-auto=validate",
                    "--spring.sql.init.mode=never",
                    "--recurring.scheduler.enabled=false",
                    "--market-data.enabled=false", "--market-data.api-key=",
                    "--ai.insights.enabled=false", "--ai.insights.api-key=",
                    "--CRON_SECRET=",
                    "--spring.devtools.restart.enabled=false",
                    "--server.address=127.0.0.1", "--server.port=0")) {
                assertThat(context).isInstanceOf(ServletWebServerApplicationContext.class);
                assertThat(((ServletWebServerApplicationContext) context).getWebServer().getPort()).isPositive();
                assertThat(context.getBean(EntityManagerFactory.class).isOpen()).isTrue();
                assertThat(context.getBeansOfType(RecurringTransactionScheduler.class)).isEmpty();
                assertThat(context.getBean(MarketDataProperties.class).isEnabled()).isFalse();
                assertThat(context.getBean(MarketDataProperties.class).getApiKey()).isEmpty();
                assertThat(context.getBean(InsightsAiProperties.class).isEnabled()).isFalse();
                assertThat(context.getBean(InsightsAiProperties.class).getApiKey()).isEmpty();
                assertThat(context.getEnvironment().getProperty("CRON_SECRET")).isEmpty();
                Flyway runtimeFlyway = context.getBean(Flyway.class);
                assertThat(runtimeFlyway.info().all()).anyMatch(m -> m.getState() == MigrationState.BASELINE);
                assertThat(runtimeFlyway.info().all()).anyMatch(m -> m.getState() == MigrationState.BASELINE_IGNORED);
                DatabaseMigrationTool.validateHistory(runtimeFlyway);
                try (var connection = context.getBean(DataSource.class).getConnection()) {
                    assertThat(scalar(connection, "show default_transaction_read_only")).isEqualTo("on");
                    assertThat(scalar(connection, "show transaction_read_only")).isEqualTo("on");
                    assertThatThrownBy(() -> sql(connection, "update public.users set username=username"))
                            .hasMessageContaining("read-only transaction");
                }
            }
            assertThat(snapshot(db)).isEqualTo(before);
        }
        try (var connection = db.connection()) {
            assertThat(scalar(connection, "select count(*) from flyway_schema_history")).isEqualTo("2");
            assertThat(scalar(connection, "select count(*) from flyway_schema_history where type='SQL' and version='1'")).isEqualTo("0");
            AuthenticationMigrationTests.verifyV1Preserved(connection);
        }
    }

    private static String snapshot(ReconciliationTestDatabase db) throws Exception {
        try (var connection = db.connection()) {
            return fingerprint(connection) + sequences(connection)
                    + scalar(connection, "select jsonb_agg(to_jsonb(h) order by installed_rank)::text from flyway_schema_history h")
                    + BaselineSchemaVerifier.inventory(connection, "public");
        }
    }
}
