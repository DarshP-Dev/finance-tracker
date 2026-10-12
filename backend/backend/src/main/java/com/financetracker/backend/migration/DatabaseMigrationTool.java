package com.financetracker.backend.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;

/** Explicit operator tool; no Spring context, schedulers, financial writes or API clients. */
public final class DatabaseMigrationTool {
    private DatabaseMigrationTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !Arrays.asList("verify", "baseline", "migrate", "validate").contains(args[0]))
            throw new IllegalArgumentException("Usage: verify | baseline <database-name> backup-verified | migrate | validate");
        String url = required("DB_URL");
        if (!url.startsWith("jdbc:postgresql://") || url.matches("(?i).*([?&](password|user|username)=|://[^/]*@).*"))
            throw new IllegalArgumentException("Use a PostgreSQL JDBC URL without embedded credentials; configure DB_USERNAME/DB_PASSWORD separately");
        String username = required("DB_USERNAME");
        String password = required("DB_PASSWORD");
        // Never print connection URLs: query strings may contain credentials.
        try (var connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            var flyway = configured(url, username, password);
            switch (args[0]) {
                case "verify" -> {
                    connection.setReadOnly(true);
                    BaselineSchemaVerifier.verify(connection, "public");
                    System.out.println("Schema matches canonical V1. No database changes made.");
                }
                case "baseline" -> {
                    if (args.length != 3 || !"backup-verified".equals(args[2]))
                        throw new IllegalArgumentException("Baseline requires database-name confirmation and backup-verified acknowledgement");
                    baseline(connection, flyway, args[1]);
                    System.out.println("Explicit baseline 1 registered; V1 SQL was not executed. No financial records changed.");
                }
                case "migrate" -> {
                    checkHistory(connection);
                    flyway.migrate();
                    flyway.validate();
                    System.out.println("Migrations applied and checksums validated.");
                }
                case "validate" -> {
                    connection.setReadOnly(true);
                    validateHistory(flyway);
                    System.out.println("Migration history and checksums validated. No database changes made.");
                }
                default -> throw new IllegalArgumentException("Unsupported command");
            }
            connection.rollback();
        }
    }

    public static Flyway configured(String url, String user, String password) {
        return Flyway.configure().dataSource(url, user, password).schemas("public").defaultSchema("public")
                .locations("classpath:db/migration").createSchemas(false).cleanDisabled(true)
                .baselineOnMigrate(false).baselineVersion("1").validateOnMigrate(true)
                .validateMigrationNaming(true).outOfOrder(false).ignoreMigrationPatterns(new String[0]).load();
    }

    public static void baseline(Connection connection, Flyway flyway, String confirmedDatabase) throws SQLException {
        String database;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select current_database()")) {
            rows.next(); database = rows.getString(1);
        }
        if (!database.equals(confirmedDatabase)) throw new IllegalArgumentException("Database confirmation does not match");
        if (historyExists(connection)) throw new IllegalStateException("Flyway history already exists; baseline refused");
        // Freeze existing application tables through verification and history registration.
        // Operators must also stop writers/DDL; this is not an online adoption procedure.
        try (var statement = connection.createStatement()) {
            statement.execute("lock table public.users, public.ai_insights, public.budgets, public.investments, "
                    + "public.market_quote_snapshots, public.notification_receipts, public.notifications, "
                    + "public.recurring_transactions, public.transactions in access exclusive mode");
        }
        BaselineSchemaVerifier.verify(connection, "public");
        flyway.baseline();
        flyway.validate();
    }

    public static boolean historyExists(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "select to_regclass('public.flyway_schema_history') is not null")) {
            rows.next(); return rows.getBoolean(1);
        }
    }

    public static void requireHistory(Connection connection) throws SQLException {
        if (!historyExists(connection)) throw new IllegalStateException("Flyway history is absent. "
                + "Existing schemas require explicit verified baseline; empty databases require an explicit migrate. "
                + "See docs/database-migrations.md.");
        checkHistory(connection);
    }

    public static void checkHistory(Connection connection) throws SQLException {
        if (!historyExists(connection)) return;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "select version, success from public.flyway_schema_history where type='BASELINE'")) {
            while (rows.next()) {
                if (!"1".equals(rows.getString(1)))
                    throw new IllegalStateException("Unsupported Flyway baseline version; expected 1. Do not repair history.");
                if (!rows.getBoolean(2))
                    throw new IllegalStateException("Flyway baseline history is not successful. Do not repair history.");
            }
        }
    }

    /** Shared CLI/startup validation. No migrate, baseline, repair or financial writes. */
    public static void validateHistory(Flyway flyway) throws SQLException {
        boolean successfulBaseline;
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
            connection.setReadOnly(true);
            requireHistory(connection);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "select exists (select 1 from public.flyway_schema_history where type='BASELINE' and version='1' and success)")) {
                rows.next(); successfulBaseline = rows.getBoolean(1);
            }
        }
        // Checksums, missing files, failed records and pending migrations must still be validated.
        flyway.validate();
        var migrations = flyway.info().all();
        var versionOne = MigrationVersion.fromVersion("1");
        boolean explicitlyBaselined = successfulBaseline && Arrays.stream(migrations).anyMatch(m ->
                m.getState() == MigrationState.BASELINE && versionOne.equals(m.getVersion()));
        var unexpected = Arrays.stream(migrations).filter(m ->
                m.getState() != MigrationState.SUCCESS && m.getState() != MigrationState.BASELINE
                && !(explicitlyBaselined && versionOne.equals(m.getVersion())
                    && m.getState() == MigrationState.BASELINE_IGNORED)).toList();
        if (!unexpected.isEmpty())
            throw new IllegalStateException("Migration history is not fully applied: " + String.join(", ",
                    unexpected.stream().map(m -> m.getVersion() + "=" + m.getState()).toList()));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be explicitly configured");
        return value;
    }
}
