package com.financetracker.backend.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Strict, read-only adoption check, stronger than Hibernate's type validation. */
public final class BaselineSchemaVerifier {
    private BaselineSchemaVerifier() {}

    public static Set<String> inventory(Connection connection, String schema) throws SQLException {
        if (!schema.matches("[a-z_][a-z0-9_]*")) throw new IllegalArgumentException("Unsupported schema name");
        try (var path = connection.prepareStatement("select set_config('search_path', ?, true)")) {
            path.setString(1, schema + ", pg_catalog");
            path.execute();
        }
        Set<String> result = new TreeSet<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery(resource("/db/baseline/schema-inventory.sql"))) {
            while (rows.next()) result.add(normalize(rows.getString(1), schema));
        }
        return result;
    }

    public static List<String> differences(Connection connection, String schema) throws SQLException {
        Set<String> expected = new TreeSet<>(resource("/db/baseline/V1__schema_manifest.txt").lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> normalize(line, "public")).toList());
        Set<String> actual = inventory(connection, schema);
        Set<String> differences = new TreeSet<>();
        expected.stream().filter(line -> !actual.contains(line)).forEach(line -> differences.add("Missing/changed: " + line));
        actual.stream().filter(line -> !expected.contains(line)).forEach(line -> differences.add("Unexpected/changed: " + line));
        return List.copyOf(differences);
    }

    public static void verify(Connection connection, String schema) throws SQLException {
        var differences = differences(connection, schema);
        if (!differences.isEmpty()) throw new IllegalStateException("Schema is incompatible with V1; no baseline was written. "
                + "Review docs/database-migrations.md.\n" + String.join("\n", differences));
    }

    private static String normalize(String signature, String schema) {
        // pg_get_indexdef qualifies the table even with search_path set. Normalize
        // only that qualification; never rewrite string literals in CHECK/defaults.
        return signature.startsWith("index|")
                ? signature.replaceFirst(" ON " + schema + "\\.", " ON ") : signature;
    }

    static String resource(String path) {
        try (var input = BaselineSchemaVerifier.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing schema verification resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema verification resource", e);
        }
    }
}
