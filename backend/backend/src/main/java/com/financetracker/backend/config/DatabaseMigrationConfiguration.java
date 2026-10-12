package com.financetracker.backend.config;

import com.financetracker.backend.migration.DatabaseMigrationTool;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationConfiguration {
    @Bean
    FlywayMigrationStrategy controlledMigrations(@Value("${app.database.migration-mode:validate}") String mode,
            @Value("${spring.jpa.hibernate.ddl-auto:validate}") String ddlMode) {
        if (!ddlMode.equals("validate"))
            throw new IllegalArgumentException("Hibernate must use ddl-auto=validate; schema changes belong to Flyway");
        if (!mode.equals("validate") && !mode.equals("migrate"))
            throw new IllegalArgumentException("DB_MIGRATION_MODE must be validate or migrate");
        return flyway -> {
            if (flyway.getConfiguration().isBaselineOnMigrate() || !flyway.getConfiguration().isCleanDisabled())
                throw new IllegalStateException("Automatic baseline and Flyway clean are prohibited");
            try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
                connection.setReadOnly(true);
                DatabaseMigrationTool.checkHistory(connection);
                if (mode.equals("validate")) DatabaseMigrationTool.requireHistory(connection);
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Cannot check Flyway history", e);
            }
            if (mode.equals("migrate")) flyway.migrate();
            else {
                flyway.validate();
                if (Arrays.stream(flyway.info().all()).anyMatch(m -> !m.getState().isApplied()))
                    throw new IllegalStateException("Pending/unsupported database migrations. Apply them through the controlled migration tool before deployment.");
            }
        };
    }
}
