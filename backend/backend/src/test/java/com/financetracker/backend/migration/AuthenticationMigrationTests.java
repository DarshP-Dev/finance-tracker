package com.financetracker.backend.migration;

import static org.assertj.core.api.Assertions.*;
import static com.financetracker.backend.migration.ReconciliationTestDatabase.*;
import java.sql.Connection;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class AuthenticationMigrationTests {
    // V1's immutable manifest still describes only V1. Exclude precisely the new
    // V2 table from this assertion; its own shape is checked separately below.
    static void verifyV1Preserved(Connection connection) throws Exception {
        var expected = new TreeSet<>(BaselineSchemaVerifier.resource("/db/baseline/V1__schema_manifest.txt").lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.startsWith("index|") ? line.replaceFirst(" ON public\\.", " ON ") : line).toList());
        var actual = new TreeSet<>(BaselineSchemaVerifier.inventory(connection, "public"));
        actual.removeIf(line -> line.split("\\|")[1].equals("refresh_token_sessions"));
        assertThat(actual).isEqualTo(expected);
    }

    @Test void v2AfterExplicitBaselinePreservesNineFinancialTablesEightSequencesAndNeverExecutesV1() throws Exception {
        var db = new ReconciliationTestDatabase(); db.reconcile();
        String records, sequences;
        try (var c = db.connection()) {
            records = fingerprint(c); sequences = sequences(c);
            DatabaseMigrationTool.baseline(c, db.flyway(), db.database); c.rollback();
        }
        assertThatThrownBy(() -> DatabaseMigrationTool.validateHistory(db.currentFlyway())).isInstanceOf(RuntimeException.class);
        assertThat(db.currentFlyway().migrate().migrationsExecuted).isEqualTo(1);
        DatabaseMigrationTool.validateHistory(db.currentFlyway());
        assertThat(db.currentFlyway().migrate().migrationsExecuted).isZero();
        try (var c = db.connection()) {
            verifyV1Preserved(c);
            assertThat(fingerprint(c)).isEqualTo(records);
            assertThat(sequences(c)).isEqualTo(sequences);
            assertThat(scalar(c,"select count(*) from flyway_schema_history where version='1' and type='SQL'")).isEqualTo("0");
            assertThat(scalar(c,"select count(*) from flyway_schema_history where version='2' and type='SQL' and success")).isEqualTo("1");
            assertThat(scalar(c,"select count(*) from refresh_token_sessions")).isEqualTo("0");
            assertThat(scalar(c,"select count(*) from information_schema.columns where table_name='refresh_token_sessions'")).isEqualTo("8");
            assertThat(scalar(c,"select count(*) from pg_constraint where conrelid='refresh_token_sessions'::regclass and convalidated")).isEqualTo("7");
            assertThat(scalar(c,"select count(*) from pg_indexes where tablename='refresh_token_sessions'")).isEqualTo("5");
            assertThatThrownBy(() -> sql(c,"insert into refresh_token_sessions values (gen_random_uuid(),1,gen_random_uuid(),'plaintext',now(),now()+interval '1 day',null,null)"))
                    .hasMessageContaining("refresh_token_hash_check"); c.rollback();
        }
    }
}
