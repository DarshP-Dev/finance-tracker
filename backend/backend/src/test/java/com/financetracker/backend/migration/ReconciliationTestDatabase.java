package com.financetracker.backend.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;

/** Synthetic records in a new database inside the guarded disposable cluster only. */
final class ReconciliationTestDatabase {
    static final String[] TABLES = {"users", "ai_insights", "transactions", "budgets", "investments",
            "recurring_transactions", "notifications", "notification_receipts", "market_quote_snapshots"};
    static final String[] LEGACY_CATEGORIES = {"HOUSING", "UTILITIES", "GROCERIES", "DINING", "TRANSPORTATION",
            "HEALTHCARE", "INSURANCE", "DEBT_PAYMENT", "ENTERTAINMENT", "SHOPPING", "EDUCATION", "TRAVEL",
            "SALARY", "INVESTMENT_INCOME", "TRANSFER", "OTHER"};
    private final Path v1Migrations;
    final String url;
    final String database;
    final String user = System.getenv().getOrDefault("TEST_DB_USERNAME", "postgres");
    final String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");

    ReconciliationTestDatabase() throws Exception {
        String root = System.getenv("TEST_DB_URL");
        DisposableDatabaseGuard.requireDisposable(root == null ? "" : root);
        database = "ft_test_reconcile_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(root, user, password)) {
            sql(admin, "create database " + database);
        }
        url = root.substring(0, root.lastIndexOf('/') + 1) + database;
        v1Migrations = Files.createTempDirectory("ft-test-v1-");
        Files.writeString(v1Migrations.resolve("V1__initial_schema.sql"), BaselineSchemaVerifier.resource("/db/migration/V1__initial_schema.sql"));
        try (var connection = connection()) {
            sql(connection, BaselineSchemaVerifier.resource("/db/legacy/local-pre-flyway-schema.sql"));
            sql(connection, """
                insert into users(email,password,username) values ('synthetic@example.invalid','test-only','synthetic');
                insert into ai_insights(generated_at,insight_text,user_id) values ('2026-10-01','Synthetic insight',1);
                insert into investments(created_at,purchase_date,purchase_price,shares,ticker,user_id) values ('2026-10-01','2026-09-30',100.1234,2.123456,'TEST',1);
                insert into recurring_transactions(active,amount,category,created_at,frequency,next_occurrence,start_date,type,user_id) values (false,10.00,'OTHER','2026-10-01','MONTHLY','2026-11-01','2026-10-01','EXPENSE',1);
                insert into notifications(created_at,message,is_read,severity,target_path,title,type,user_id) values ('2026-10-01','Synthetic',false,'WARNING','/budgets','Synthetic','BUDGET',1);
                insert into notification_receipts(reference_key,user_id) values ('synthetic:1',1);
                insert into market_quote_snapshots(symbol,created_at,currency,fetched_at,price,source,updated_at,confirmed_close,session_date) values ('TEST','2026-10-01','USD','2026-10-01',123.456789,'TEST','2026-10-01',true,'2026-09-30');
                """);
            for (String category : LEGACY_CATEGORIES) {
                try (var insert = connection.prepareStatement("insert into transactions(amount,category,created_at,transaction_date,type,user_id) values (12.3400,?,'2026-10-01','2026-09-30','EXPENSE',1)")) {
                    insert.setString(1, category); insert.executeUpdate();
                }
                try (var insert = connection.prepareStatement("insert into budgets(category,created_at,budget_month,monthly_limit,user_id) values (?,'2026-10-01','2026-10-01',100.0000,1)")) {
                    insert.setString(1, category); insert.executeUpdate();
                }
            }
            connection.commit();
        }
    }

    Connection connection() throws Exception {
        var connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        return connection;
    }

    Flyway flyway() { return Flyway.configure().configuration(DatabaseMigrationTool.configured(url, user, password).getConfiguration())
            .locations("filesystem:" + v1Migrations).load(); }

    Flyway currentFlyway() { return DatabaseMigrationTool.configured(url, user, password); }

    void approve(Connection connection) throws Exception {
        try (var settings = connection.prepareStatement("select set_config('finance_tracker.reconciliation_approved','approved-local-four-constraints',false), set_config('finance_tracker.reconciliation_database',?,false)")) {
            settings.setString(1, database); settings.execute();
        }
        connection.commit();
    }

    void reconcile() throws Exception {
        try (var connection = connection()) {
            approve(connection);
            script(connection, "10_reconcile_local_constraints.sql");
        }
    }

    static void script(Connection connection, String name) throws Exception {
        sql(connection, Files.readString(Path.of("database", "reconciliation", name)));
    }

    static void sql(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    static String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getString(1);
        }
    }

    static String fingerprint(Connection connection) throws Exception {
        var result = new StringBuilder();
        for (String table : TABLES) {
            result.append(table).append(scalar(connection,
                    "select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text)::text,'[]') from public." + table + " t"));
        }
        return result.toString();
    }

    static String sequences(Connection connection) throws Exception {
        var result = new StringBuilder();
        for (String table : TABLES) {
            if (!table.equals("market_quote_snapshots")) {
                result.append(table).append(scalar(connection,
                        "select last_value::text || ':' || is_called::text from public." + table + "_id_seq"));
            }
        }
        return result.toString();
    }
}
