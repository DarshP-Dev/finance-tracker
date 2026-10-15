package com.financetracker.backend.migration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.financetracker.backend.config.DatabaseMigrationConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.test.util.ReflectionTestUtils;

class RemoteStartupMigrationSafetyTests {
    @Test void remoteStartupCannotRunMigrationsEvenWithMigrateModeConfigured() throws Exception {
        var flyway=mock(Flyway.class); var config=mock(Configuration.class);
        var source=mock(javax.sql.DataSource.class); var connection=mock(java.sql.Connection.class);
        var metadata=mock(java.sql.DatabaseMetaData.class);
        when(flyway.getConfiguration()).thenReturn(config);
        when(config.isCleanDisabled()).thenReturn(true);
        when(config.getDataSource()).thenReturn(source);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://remote.example.invalid/finance_tracker_db");
        var strategy=(FlywayMigrationStrategy) ReflectionTestUtils.invokeMethod(new DatabaseMigrationConfiguration(),
                "controlledMigrations","migrate","validate");
        assertThatThrownBy(() -> strategy.migrate(flyway)).hasMessageContaining("restricted to loopback");
        verify(flyway,never()).migrate();
        verify(connection,never()).createStatement();
    }
}
