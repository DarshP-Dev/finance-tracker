package com.financetracker.backend.migration;

import static org.assertj.core.api.Assertions.*;
import static com.financetracker.backend.migration.ReconciliationTestDatabase.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import com.financetracker.backend.config.DatabaseMigrationConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReconciliationTests {
    private ReconciliationTestDatabase db;
    @TempDir Path migrations;

    @BeforeEach void legacySchema() throws Exception { db = new ReconciliationTestDatabase(); }

    @Test void capturedLocalSchemaHasExactlyTheFourReportedConstraintDifferences() throws Exception {
        try (var connection = db.connection()) {
            var differences = BaselineSchemaVerifier.differences(connection, "public");
            assertThat(differences).hasSize(6); // Two changed checks give four signatures; two missing checks give two.
            assertThat(differences).allMatch(value -> value.startsWith("Missing/changed: constraint|")
                    || value.startsWith("Unexpected/changed: constraint|"));
            assertThatThrownBy(() -> DatabaseMigrationTool.baseline(connection, db.flyway(), db.database))
                    .hasMessageContaining("Schema is incompatible with V1");
            assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse();
        }
    }

    @Test void reconciliationMatchesFullManifestPreservesEveryRecordAndAllSequences() throws Exception {
        try (var connection = db.connection()) {
            String before = fingerprint(connection);
            String counters = sequences(connection);
            script(connection, "00_read_only_preflight.sql");
            db.approve(connection);
            script(connection, "10_reconcile_local_constraints.sql");
            script(connection, "20_read_only_postflight.sql");
            BaselineSchemaVerifier.verify(connection, "public");
            assertThat(fingerprint(connection)).isEqualTo(before);
            assertThat(sequences(connection)).isEqualTo(counters);
            assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse();
            assertThat(scalar(connection, "select count(distinct category) from transactions")).isEqualTo("16");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"FOOD", "RENT", "HEALTH"})
    void widenedConstraintsAcceptEveryAddedValidCategory(String category) throws Exception {
        db.reconcile();
        try (var connection = db.connection()) {
            sql(connection, "insert into transactions(amount,category,created_at,transaction_date,type,user_id) values (2.5000,'" + category + "',now(),current_date,'EXPENSE',1)");
            sql(connection, "insert into budgets(category,created_at,budget_month,monthly_limit,user_id) values ('" + category + "',now(),current_date,3.0000,1)");
            assertThat(scalar(connection, "select count(*) from transactions where category='" + category + "'")).isEqualTo("1");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"shares=0", "shares=-0.000001", "purchase_price=0", "purchase_price=-0.0001"})
    void invalidExistingInvestmentsStopWithoutChangingAnyDataOrSchema(String mutation) throws Exception {
        try (var connection = db.connection()) {
            sql(connection, "update investments set " + mutation); connection.commit();
            var beforeSchema = BaselineSchemaVerifier.inventory(connection, "public");
            String before = fingerprint(connection);
            db.approve(connection);
            assertThatThrownBy(() -> script(connection, "10_reconcile_local_constraints.sql"))
                    .hasMessageContaining("Existing investment values violate positivity");
            connection.rollback();
            assertThat(BaselineSchemaVerifier.inventory(connection, "public")).isEqualTo(beforeSchema);
            assertThat(fingerprint(connection)).isEqualTo(before);
            assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"shares=0", "shares=-0.000001", "purchase_price=0", "purchase_price=-0.0001"})
    void newNonpositiveInvestmentWritesAreRejectedByDatabase(String mutation) throws Exception {
        db.reconcile();
        try (var connection = db.connection()) {
            assertThatThrownBy(() -> sql(connection, "update investments set " + mutation))
                    .hasMessageContaining("violates check constraint");
        }
    }

    @Test void unknownCategoryStillFailsAfterReconciliation() throws Exception {
        db.reconcile();
        for (String table : new String[]{"budgets", "transactions"}) {
            try (var connection = db.connection()) {
                assertThatThrownBy(() -> sql(connection, "update " + table + " set category='INVALID'"))
                        .hasMessageContaining("violates check constraint");
            }
        }
    }

    @Test void missingApprovalRefusesAnyChange() throws Exception {
        try (var connection = db.connection()) {
            var beforeSchema = BaselineSchemaVerifier.inventory(connection, "public");
            String before = fingerprint(connection);
            assertThatThrownBy(() -> script(connection, "10_reconcile_local_constraints.sql"))
                    .hasMessageContaining("requires reviewed local approval");
            connection.rollback();
            assertThat(BaselineSchemaVerifier.inventory(connection, "public")).isEqualTo(beforeSchema);
            assertThat(fingerprint(connection)).isEqualTo(before);
        }
    }

    @Test void activeWriterLockTimesOutWithoutPartialChanges() throws Exception {
        try (var writer = db.connection(); var connection = db.connection()) {
            sql(writer, "select id from transactions where id=1 for update");
            // A writer holds a table lock, but ordinary reads below still work.
            var beforeSchema = BaselineSchemaVerifier.inventory(connection, "public");
            String before = fingerprint(connection);
            db.approve(connection);
            assertThatThrownBy(() -> script(connection, "10_reconcile_local_constraints.sql"))
                    .hasMessageContaining("lock timeout");
            connection.rollback();
            assertThat(BaselineSchemaVerifier.inventory(connection, "public")).isEqualTo(beforeSchema);
            assertThat(fingerprint(connection)).isEqualTo(before);
            writer.rollback();
        }
    }

    @ParameterizedTest @ValueSource(strings = {
            "select set_config('finance_tracker.reconciliation_approved','',false)",
            "select set_config('finance_tracker.reconciliation_database','wrong_database',false)",
            "create table public.flyway_schema_history(sentinel integer)",
            "alter table budgets rename constraint budgets_category_check to unknown_category_check",
            "alter table investments add constraint ck_investments_shares_positive check (shares > 0)"
    })
    void unexpectedTargetApprovalHistoryOrConstraintStateIsRejected(String mutation) throws Exception {
        try (var connection = db.connection()) {
            db.approve(connection);
            sql(connection, mutation); connection.commit();
            var beforeSchema = BaselineSchemaVerifier.inventory(connection, "public");
            String before = fingerprint(connection);
            assertThatThrownBy(() -> script(connection, "10_reconcile_local_constraints.sql"))
                    .isInstanceOf(java.sql.SQLException.class);
            connection.rollback();
            assertThat(BaselineSchemaVerifier.inventory(connection, "public")).isEqualTo(beforeSchema);
            assertThat(fingerprint(connection)).isEqualTo(before);
        }
    }

    @Test void ddlFailureAfterFirstTableRepairRollsBackAllFourChanges() throws Exception {
        try (var connection = db.connection()) {
            sql(connection, "alter table transactions add constraint transactions_category_v1_reconciliation check (amount is not null)");
            connection.commit();
            var beforeSchema = BaselineSchemaVerifier.inventory(connection, "public");
            String before = fingerprint(connection);
            db.approve(connection);
            assertThatThrownBy(() -> script(connection, "10_reconcile_local_constraints.sql"))
                    .hasMessageContaining("already exists");
            connection.rollback();
            assertThat(BaselineSchemaVerifier.inventory(connection, "public")).isEqualTo(beforeSchema);
            assertThat(fingerprint(connection)).isEqualTo(before);
            assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse();
        }
    }

    @Test void repeatedReconciliationRefusesAlreadyCorrectedState() throws Exception {
        db.reconcile();
        try (var connection = db.connection()) {
            String before = fingerprint(connection);
            db.approve(connection);
            assertThatThrownBy(() -> script(connection, "10_reconcile_local_constraints.sql"))
                    .hasMessageContaining("Unexpected category constraint state");
            connection.rollback();
            BaselineSchemaVerifier.verify(connection, "public");
            assertThat(fingerprint(connection)).isEqualTo(before);
        }
    }

    @Test void explicitBaselineSkipsV1AndFutureMigrationWorksWithoutChangingFinancialRecords() throws Exception {
        db.reconcile();
        try (var connection = db.connection()) {
            String before = fingerprint(connection);
            String counters = sequences(connection);
            DatabaseMigrationTool.baseline(connection, db.flyway(), db.database);
            connection.rollback(); // Releases table locks; Flyway committed its own history transaction.
            assertThat(db.flyway().migrate().migrationsExecuted).isZero();
            assertThat(scalar(connection, "select type from flyway_schema_history where version='1'")).isEqualTo("BASELINE");
            assertThat(fingerprint(connection)).isEqualTo(before);
            assertThat(sequences(connection)).isEqualTo(counters);
            connection.rollback();
            FlywayMigrationStrategy validationOnly = ReflectionTestUtils.invokeMethod(
                    new DatabaseMigrationConfiguration(), "controlledMigrations", "validate", "validate");
            validationOnly.migrate(db.flyway());
            Files.writeString(migrations.resolve("V1__initial_schema.sql"), BaselineSchemaVerifier.resource("/db/migration/V1__initial_schema.sql"));
            Files.writeString(migrations.resolve("V2__synthetic_index.sql"), "create index idx_test_reconciliation_amount on public.transactions(amount);");
            var future = Flyway.configure().dataSource(db.url, db.user, db.password).schemas("public")
                    .cleanDisabled(true).baselineOnMigrate(false).ignoreMigrationPatterns(new String[0])
                    .locations("filesystem:" + migrations).load();
            assertThatThrownBy(() -> validationOnly.migrate(future))
                    .hasMessageContaining("Detected resolved migration not applied to database: 2");
            assertThat(scalar(connection, "select to_regclass('public.idx_test_reconciliation_amount') is null")).isEqualTo("t");
            connection.rollback();
            assertThat(future.migrate().migrationsExecuted).isEqualTo(1);
            future.validate();
            validationOnly.migrate(future);
            assertThat(future.migrate().migrationsExecuted).isZero();
            assertThat(scalar(connection, "select count(*) from flyway_schema_history where version='2' and success")).isEqualTo("1");
            assertThat(fingerprint(connection)).isEqualTo(before);
            assertThat(sequences(connection)).isEqualTo(counters);
        }
    }
}
