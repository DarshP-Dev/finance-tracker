package com.financetracker.backend.migration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.financetracker.backend.migration.ReconciliationTestDatabase.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.financetracker.backend.config.DatabaseMigrationConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.test.util.ReflectionTestUtils;

/** All history mutations, baselines and migrations here target a new disposable database. */
class MigrationHistoryValidationTests {
    private ReconciliationTestDatabase db;
    @TempDir Path migrations;

    @BeforeEach void explicitDisposableBaseline() throws Exception {
        db = new ReconciliationTestDatabase();
        db.reconcile();
        try (var connection = db.connection()) {
            DatabaseMigrationTool.baseline(connection, db.flyway(), db.database);
            connection.rollback();
        }
        Files.writeString(migrations.resolve("V1__initial_schema.sql"),
                BaselineSchemaVerifier.resource("/db/migration/V1__initial_schema.sql"));
    }

    @Test void legitimateBaselineHasExpectedIgnoredV1AndPassesBothValidationPaths() throws Exception {
        assertThat(db.flyway().info().all()).anyMatch(m -> m.getState() == MigrationState.BASELINE
                && MigrationVersion.fromVersion("1").equals(m.getVersion()));
        assertThat(db.flyway().info().all()).anyMatch(m -> m.getState() == MigrationState.BASELINE_IGNORED
                && MigrationVersion.fromVersion("1").equals(m.getVersion()));
        String before = snapshot();
        DatabaseMigrationTool.validateHistory(db.flyway());
        startupValidation().migrate(db.flyway());
        assertThat(snapshot()).isEqualTo(before);
        try (var connection = db.connection()) {
            assertThat(scalar(connection, "select count(*) from flyway_schema_history where type='SQL'")).isEqualTo("0");
        }
    }

    @Test void repeatedCliValidationSucceedsWithoutAnyDatabaseChanges() throws Exception {
        String before = snapshot();
        for (int attempt = 0; attempt < 2; attempt++) {
            Path output = migrations.resolve("cli-validation-" + attempt + ".log");
            String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            var command = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                    DatabaseMigrationTool.class.getName(), "validate");
            // Explicit protected-target guard before constructing the test-only child environment.
            DisposableDatabaseGuard.requireDisposable(db.url);
            command.environment().put("DB_URL", db.url + "?options=-c%20default_transaction_read_only%3Don");
            command.environment().put("DB_USERNAME", db.user);
            command.environment().put("DB_PASSWORD", db.password.isBlank() ? "synthetic-test-only" : db.password);
            command.redirectErrorStream(true).redirectOutput(output.toFile());
            var process = command.start();
            boolean finished = process.waitFor(35, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            assertThat(finished).as("CLI validation completed within its timeout").isTrue();
            String log = Files.readString(output);
            assertThat(process.exitValue()).as(log).isZero();
            assertThat(log).contains("Migration history and checksums validated. No database changes made.");
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    @ParameterizedTest @ValueSource(strings = {
            "drop table public.flyway_schema_history",
            "delete from public.flyway_schema_history",
            "update public.flyway_schema_history set version='2'",
            "update public.flyway_schema_history set success=false",
            "update public.flyway_schema_history set type='SQL'"
    })
    void absentIncorrectOrUnsuccessfulBaselineHistoryIsRejectedUnchanged(String corruption) throws Exception {
        try (var connection = db.connection()) { sql(connection, corruption); connection.commit(); }
        assertRejectedWithoutChanges(db.flyway());
    }

    @Test void ignoredV1CannotBeAuthorizedByMigrationInfoWithoutGenuineHistory() throws Exception {
        try (var connection = db.connection()) {
            sql(connection, "delete from flyway_schema_history"); connection.commit();
        }
        // Model an info result that claims a baseline despite an empty actual history table.
        // Successful SQL history, not the displayed state alone, must authorize the exception.
        var reportedBaseline = mock(MigrationInfo.class);
        when(reportedBaseline.getVersion()).thenReturn(MigrationVersion.fromVersion("1"));
        when(reportedBaseline.getState()).thenReturn(MigrationState.BASELINE);
        var ignoredV1 = mock(MigrationInfo.class);
        when(ignoredV1.getVersion()).thenReturn(MigrationVersion.fromVersion("1"));
        when(ignoredV1.getState()).thenReturn(MigrationState.BASELINE_IGNORED);
        var info = mock(MigrationInfoService.class);
        when(info.all()).thenReturn(new MigrationInfo[]{reportedBaseline, ignoredV1});
        var claimed = mock(Flyway.class);
        when(claimed.getConfiguration()).thenReturn(db.flyway().getConfiguration());
        when(claimed.info()).thenReturn(info);
        String before = snapshot();
        assertThatThrownBy(() -> DatabaseMigrationTool.validateHistory(claimed))
                .hasMessageContaining("1=BASELINE_IGNORED");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void pendingFutureMigrationIsRejectedWithoutExecutingItsSql() throws Exception {
        Files.writeString(migrations.resolve("V2__pending.sql"), "create table pending_change(id bigint);");
        var future = filesystemFlyway();
        assertThat(future.info().all()).anyMatch(m -> m.getState() == MigrationState.PENDING);
        assertRejectedWithoutChanges(future);
        try (var connection = db.connection()) {
            assertThat(scalar(connection, "select to_regclass('public.pending_change') is null")).isEqualTo("t");
        }
    }

    @Test void pendingFutureMigrationRemainsRejectedEvenIfFlywayValidationIgnoresPending() throws Exception {
        Files.writeString(migrations.resolve("V2__pending.sql"), "create table pending_change(id bigint);");
        var permissive = filesystemFlyway("*:pending");
        permissive.validate(); // Own validation success must not substitute for applied-state checks.
        String before = snapshot();
        assertThatThrownBy(() -> DatabaseMigrationTool.validateHistory(permissive))
                .hasMessageContaining("2=PENDING");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void unexpectedIgnoredMigrationIsRejectedEvenIfFlywayAllowsIgnored() throws Exception {
        Files.writeString(migrations.resolve("V3__later.sql"), "create index idx_test_later on transactions(amount);");
        filesystemFlyway().migrate();
        Files.writeString(migrations.resolve("V2__ignored.sql"), "create table ignored_change(id bigint);");
        var permissive = filesystemFlyway("*:ignored");
        assertThat(permissive.info().all()).anyMatch(m -> m.getState() == MigrationState.IGNORED);
        permissive.validate();
        String before = snapshot();
        assertThatThrownBy(() -> DatabaseMigrationTool.validateHistory(permissive))
                .hasMessageContaining("2=IGNORED");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void failedMigrationRecordIsRejectedWithoutRepairingHistory() throws Exception {
        Files.writeString(migrations.resolve("V2__failed.sql"), "select 1;");
        try (var connection = db.connection()) {
            // PostgreSQL transactional failures normally roll back history; deliberately model
            // a recorded nontransactional failure in this disposable history table only.
            sql(connection, """
                insert into flyway_schema_history(installed_rank,version,description,type,script,
                    installed_by,execution_time,success)
                values(2,'2','failed','SQL','V2__failed.sql','synthetic-test',0,false)
                """);
            connection.commit();
        }
        var failed = filesystemFlyway();
        assertThat(failed.info().all()).anyMatch(m -> m.getState() == MigrationState.FAILED);
        assertRejectedWithoutChanges(failed);
    }

    @Test void changedAppliedChecksumIsRejected() throws Exception {
        Path v2 = migrations.resolve("V2__applied.sql");
        Files.writeString(v2, "create index idx_test_applied on transactions(amount);");
        filesystemFlyway().migrate();
        Files.writeString(v2, Files.readString(v2) + "\n-- modified checksum\n");
        String before = snapshot();
        assertThatThrownBy(() -> DatabaseMigrationTool.validateHistory(filesystemFlyway()))
                .hasMessageContaining("checksum mismatch");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void missingAppliedMigrationIsRejected() throws Exception {
        Path v2 = migrations.resolve("V2__applied.sql");
        Files.writeString(v2, "create index idx_test_applied on transactions(amount);");
        filesystemFlyway().migrate();
        Files.delete(v2);
        assertRejectedWithoutChanges(filesystemFlyway());
    }

    private Flyway filesystemFlyway(String... ignoredPatterns) {
        return Flyway.configure().dataSource(db.url, db.user, db.password).schemas("public")
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false)
                .ignoreMigrationPatterns(ignoredPatterns).locations("filesystem:" + migrations).load();
    }

    private FlywayMigrationStrategy startupValidation() {
        return ReflectionTestUtils.invokeMethod(new DatabaseMigrationConfiguration(),
                "controlledMigrations", "validate", "validate");
    }

    private void assertRejectedWithoutChanges(Flyway flyway) throws Exception {
        String before = snapshot();
        assertThatThrownBy(() -> DatabaseMigrationTool.validateHistory(flyway)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> startupValidation().migrate(flyway)).isInstanceOf(RuntimeException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    private String snapshot() throws Exception {
        try (var connection = db.connection()) {
            String history = DatabaseMigrationTool.historyExists(connection)
                    ? scalar(connection, "select coalesce(jsonb_agg(to_jsonb(h) order by installed_rank)::text,'[]') from flyway_schema_history h")
                    : "absent";
            return fingerprint(connection) + sequences(connection) + history
                    + BaselineSchemaVerifier.inventory(connection, "public");
        }
    }
}
