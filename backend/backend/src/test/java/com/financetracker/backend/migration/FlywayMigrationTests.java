package com.financetracker.backend.migration;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.financetracker.backend.config.DatabaseMigrationConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.test.util.ReflectionTestUtils;

/** Every case owns a new database inside run-tests.ps1's disposable cluster. */
class FlywayMigrationTests {
    private String url;
    private String user;
    private String password;
    private String database;
    private Flyway flyway;
    @TempDir Path migrations;

    @BeforeEach void isolatedDatabase() throws Exception {
        String root = System.getenv("TEST_DB_URL");
        DisposableDatabaseGuard.requireDisposable(root == null ? "" : root);
        user = System.getenv().getOrDefault("TEST_DB_USERNAME", "postgres");
        password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
        database = "ft_test_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(root, user, password); var sql = admin.createStatement()) {
            sql.execute("create database " + database);
        }
        url = root.substring(0, root.lastIndexOf('/') + 1) + database;
        flyway = DatabaseMigrationTool.configured(url, user, password);
    }

    @Test void freshDatabaseCreatesCanonicalSchemaAndOneHistoryEntry() throws Exception {
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        try (var connection = connection()) {
            BaselineSchemaVerifier.verify(connection, "public");
            assertThat(scalar(connection, "select count(*) from flyway_schema_history where version='1' and type='SQL' and success")).isEqualTo("1");
            assertThat(scalar(connection, "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE'")).isEqualTo("10");
        }
    }

    @Test void restartingDoesNotReapplySchemaOrChangeIdentifiers() throws Exception {
        flyway.migrate();
        try (var connection = connection()) {
            sql(connection, "insert into users(email,password,username) values ('restart@example.invalid','test','restart')");
            connection.commit();
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        try (var connection = connection()) {
            assertThat(scalar(connection, "select id from users where username='restart'")).isEqualTo("1");
            assertThat(scalar(connection, "select count(*) from flyway_schema_history")).isEqualTo("1");
            BaselineSchemaVerifier.verify(connection, "public");
        }
    }

    @Test void explicitBaselinePreservesEveryRecordAndSequenceValueAndSkipsV1() throws Exception {
        createLegacyCanonicalSchema();
        seed();
        try (var connection = connection()) {
            String before = dataFingerprint(connection);
            String sequence = scalar(connection, "select last_value from users_id_seq");
            DatabaseMigrationTool.baseline(connection, flyway, database);
            connection.rollback(); // releases locks; baseline history has its own committed transaction
            assertThat(dataFingerprint(connection)).isEqualTo(before);
            assertThat(scalar(connection, "select last_value from users_id_seq")).isEqualTo(sequence);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(scalar(connection, "select type from flyway_schema_history where version='1'")).isEqualTo("BASELINE");
            assertThat(dataFingerprint(connection)).isEqualTo(before);
        }
    }

    @Test void nonemptyUnbaselinedDatabaseFailsWithoutCreatingHistoryOrChangingData() throws Exception {
        try (var connection = connection()) {
            sql(connection, "create table sentinel(id bigint primary key); insert into sentinel values (42)");
            connection.commit();
        }
        assertThatThrownBy(flyway::migrate).hasMessageContaining("non-empty schema");
        try (var connection = connection()) {
            assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse();
            assertThat(scalar(connection, "select id from sentinel")).isEqualTo("42");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "alter table transactions alter column amount type numeric(19,2)",
        "alter table transactions alter column merchant set not null",
        "alter table transactions alter column amount set default 0",
        "alter table transactions drop constraint fk_transactions_user",
        "alter table users drop constraint uk_users_email",
        "drop index idx_recurring_transactions_due",
        "create index unexpected_index on transactions(amount)",
        "alter sequence users_id_seq increment by 2",
        "create table unexpected_table(id bigint)",
        "create function unexpected_function() returns integer language sql as 'select 1'",
        "alter table transactions enable row level security",
        "alter table investments drop constraint ck_investments_shares_positive",
        "alter table budgets drop constraint budgets_category_check; alter table budgets add constraint budgets_category_check check (category in ('OTHER'))"
    })
    void incompatibleSchemaIsRejectedWithoutHistory(String mutation) throws Exception {
        createLegacyCanonicalSchema();
        try (var connection = connection()) {
            sql(connection, mutation); connection.commit();
            assertThatThrownBy(() -> DatabaseMigrationTool.baseline(connection, flyway, database))
                    .hasMessageContaining("Schema is incompatible with V1");
            assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse();
        }
    }

    @Test void readOnlyVerifierWorksWithoutWritingHistory() throws Exception {
        createLegacyCanonicalSchema();
        try (var connection = connection()) {
            connection.setReadOnly(true);
            BaselineSchemaVerifier.verify(connection, "public");
            assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse();
        }
    }

    @Test void databaseNameConfirmationIsRequiredBeforeBaseline() throws Exception {
        createLegacyCanonicalSchema();
        try (var connection = connection()) {
            assertThatThrownBy(() -> DatabaseMigrationTool.baseline(connection, flyway, "wrong_database"))
                    .hasMessageContaining("confirmation");
            assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse();
        }
    }

    @Test void alreadyAdoptedSchemaCannotBeBaselinedAgain() throws Exception {
        flyway.migrate();
        try (var connection = connection()) {
            assertThatThrownBy(() -> DatabaseMigrationTool.baseline(connection, flyway, database))
                    .hasMessageContaining("history already exists");
        }
    }

    @Test void incorrectBaselineVersionIsRejected() throws Exception {
        createLegacyCanonicalSchema();
        Flyway.configure().dataSource(url,user,password).baselineVersion("2").load().baseline();
        try (var connection = connection()) {
            assertThatThrownBy(() -> DatabaseMigrationTool.checkHistory(connection))
                    .hasMessageContaining("baseline version");
        }
    }

    @Test void changedChecksumIsRejected() throws Exception {
        flyway.migrate();
        Files.writeString(migrations.resolve("V1__initial_schema.sql"),
                BaselineSchemaVerifier.resource("/db/migration/V1__initial_schema.sql") + "\n-- changed applied migration\n");
        assertThatThrownBy(() -> filesystemFlyway().validate()).hasMessageContaining("checksum mismatch");
    }

    @Test void missingAppliedMigrationIsRejected() throws Exception {
        flyway.migrate();
        assertThatThrownBy(() -> filesystemFlyway().validate()).hasMessageContaining("not resolved locally");
    }

    @Test void unknownFutureHistoryIsRejected() throws Exception {
        flyway.migrate();
        try (var connection = connection()) {
            sql(connection, "update flyway_schema_history set version='42' where version='1'"); connection.commit();
        }
        assertThatThrownBy(flyway::validate).hasMessageContaining("not resolved locally");
    }

    @Test void nextVersionAfterExplicitBaselineRunsNormally() throws Exception {
        createLegacyCanonicalSchema();
        try (var connection = connection()) {
            DatabaseMigrationTool.baseline(connection, flyway, database); connection.rollback();
        }
        copyV1();
        Files.writeString(migrations.resolve("V2__test_add_index.sql"), "create index idx_test_future on transactions(amount);");
        assertThat(filesystemFlyway().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(filesystemFlyway().migrate().migrationsExecuted).isZero();
        filesystemFlyway().validate();
        try (var connection = connection()) {
            assertThat(scalar(connection,"select string_agg(version,',' order by installed_rank) from flyway_schema_history")).isEqualTo("1,2");
        }
    }

    @Test void failedTransactionalMigrationLeavesSchemaAndDataIntact() throws Exception {
        flyway.migrate(); seed(); copyV1();
        Files.writeString(migrations.resolve("V2__test_failure.sql"), "create table failed_change(id bigint); select 1/0;");
        try (var connection = connection()) {
            String before = dataFingerprint(connection);
            assertThatThrownBy(() -> filesystemFlyway().migrate()).hasMessageContaining("division by zero");
            assertThat(scalar(connection,"select to_regclass('public.failed_change') is null")).isEqualTo("t");
            assertThat(dataFingerprint(connection)).isEqualTo(before);
            assertThat(scalar(connection,"select count(*) from flyway_schema_history")).isEqualTo("1");
        }
    }

    @Test void foreignKeysUniquenessIdentityDeduplicationAndSnapshotsArePreserved() throws Exception {
        flyway.migrate(); seed();
        try (var connection = connection()) {
            assertThat(scalar(connection,"select price::text from market_quote_snapshots where symbol='TEST'")).isEqualTo("123.456789000000");
            assertThat(scalar(connection,"select count(*) from notification_receipts")).isEqualTo("1");
            connection.rollback();
            assertThatThrownBy(() -> sql(connection,"insert into notification_receipts(reference_key,user_id) values ('event:1',1)"))
                    .hasMessageContaining("uk_notification_receipts_user_key"); connection.rollback();
            assertThatThrownBy(() -> sql(connection,"insert into budgets(category,created_at,budget_month,monthly_limit,user_id) values ('OTHER',now(),current_date,100,99999)"))
                    .hasMessageContaining("fk_budgets_user"); connection.rollback();
            sql(connection,"insert into users(email,password,username) values ('second@example.invalid','test','second')");
            assertThat(scalar(connection,"select id from users where username='second'")).isEqualTo("2");
            sql(connection,"delete from ai_insights; delete from budgets; delete from investments; delete from recurring_transactions; delete from transactions; delete from users where id=1");
            assertThat(scalar(connection,"select count(*) from notifications")).isEqualTo("0");
            assertThat(scalar(connection,"select count(*) from notification_receipts")).isEqualTo("0");
        }
    }

    @Test void guardRefusesRealLocalAndRemoteTargets() {
        for (String target : new String[]{"jdbc:postgresql://localhost:5432/finance_tracker_db", "jdbc:postgresql://neon.example:5432/ft_test_x",
                "jdbc:postgresql://127.0.0.1:55432/finance_tracker_db", "jdbc:postgresql://127.0.0.1:55432/ft_test_x?service=production"})
            assertThatThrownBy(() -> DisposableDatabaseGuard.requireDisposable(target)).hasMessageContaining("isolated");
    }

    @Test void validationOnlyStartupPreservesPopulatedSchemaAndHistory() throws Exception {
        flyway.migrate(); seed();
        try(var connection=connection()) {
            String before=dataFingerprint(connection);
            validationOnly().migrate(flyway);
            validationOnly().migrate(flyway);
            assertThat(dataFingerprint(connection)).isEqualTo(before);
            assertThat(scalar(connection,"select count(*) from flyway_schema_history")).isEqualTo("1");
            BaselineSchemaVerifier.verify(connection,"public");
        }
    }

    @Test void validationOnlyStartupNeverInitializesEvenAnEmptyDatabase() throws Exception {
        assertThatThrownBy(()->validationOnly().migrate(flyway)).hasMessageContaining("history is absent");
        try(var connection=connection()) { assertThat(DatabaseMigrationTool.historyExists(connection)).isFalse(); }
    }

    @Test void validationOnlyStartupRejectsPendingMigrationWithoutExecutingIt() throws Exception {
        flyway.migrate(); copyV1();
        Files.writeString(migrations.resolve("V2__test_pending.sql"),"create table pending_change(id bigint);");
        assertThatThrownBy(()->validationOnly().migrate(filesystemFlyway())).isInstanceOf(RuntimeException.class);
        try(var connection=connection()) {
            assertThat(scalar(connection,"select to_regclass('public.pending_change') is null")).isEqualTo("t");
            assertThat(scalar(connection,"select count(*) from flyway_schema_history")).isEqualTo("1");
        }
    }

    private FlywayMigrationStrategy validationOnly() {
        return ReflectionTestUtils.invokeMethod(new DatabaseMigrationConfiguration(),"controlledMigrations","validate","validate");
    }

    private Connection connection() throws Exception {
        var connection = DriverManager.getConnection(url,user,password);
        connection.setAutoCommit(false); return connection;
    }
    private void createLegacyCanonicalSchema() throws Exception {
        try (var connection = connection()) {
            sql(connection, BaselineSchemaVerifier.resource("/db/migration/V1__initial_schema.sql")); connection.commit();
        }
    }
    private Flyway filesystemFlyway() {
        return Flyway.configure().dataSource(url,user,password).schemas("public").cleanDisabled(true)
                .baselineOnMigrate(false).ignoreMigrationPatterns(new String[0]).locations("filesystem:"+migrations).load();
    }
    private void copyV1() throws Exception {
        Files.writeString(migrations.resolve("V1__initial_schema.sql"),BaselineSchemaVerifier.resource("/db/migration/V1__initial_schema.sql"));
    }
    private static void sql(Connection c,String sql) throws Exception { try(var s=c.createStatement()) { s.execute(sql); } }
    private static String scalar(Connection c,String sql) throws Exception {
        try(var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }
    private void seed() throws Exception {
        try(var connection=connection()) {
            sql(connection,"""
                insert into users(email,password,username) values ('one@example.invalid','test','one');
                insert into ai_insights(generated_at,insight_text,user_id) values (now(),'Recorded insight',1);
                insert into transactions(amount,category,created_at,transaction_date,type,user_id) values (12.3400,'DINING',now(),current_date,'EXPENSE',1);
                insert into budgets(category,created_at,budget_month,monthly_limit,user_id) values ('DINING',now(),current_date,100.0000,1);
                insert into investments(created_at,purchase_date,purchase_price,shares,ticker,user_id) values (now(),current_date,100.1234,2.123456,'TEST',1);
                insert into recurring_transactions(active,amount,category,created_at,frequency,next_occurrence,start_date,type,user_id) values (false,10.00,'OTHER',now(),'MONTHLY',current_date,current_date,'EXPENSE',1);
                insert into notifications(created_at,message,is_read,severity,target_path,title,type,user_id) values (now(),'Test',false,'WARNING','/budgets','Test','BUDGET',1);
                insert into notification_receipts(reference_key,user_id) values ('event:1',1);
                insert into market_quote_snapshots(symbol,created_at,currency,fetched_at,price,source,updated_at,confirmed_close,session_date) values ('TEST',now(),'USD',now(),123.456789,'TEST',now(),true,current_date);
                """);
            connection.commit();
        }
    }
    private String dataFingerprint(Connection c) throws Exception {
        var result = new StringBuilder();
        for(String table : new String[]{"users","ai_insights","transactions","budgets","investments","recurring_transactions","notifications","notification_receipts","market_quote_snapshots"})
            result.append(scalar(c,"select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text)::text,'[]') from "+table+" t"));
        return result.toString();
    }
}
