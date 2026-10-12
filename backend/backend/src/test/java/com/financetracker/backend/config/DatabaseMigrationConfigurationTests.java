package com.financetracker.backend.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;

class DatabaseMigrationConfigurationTests {
    private final DatabaseMigrationConfiguration configuration=new DatabaseMigrationConfiguration();

    @Test void rejectsUnsupportedModes() {
        assertThatThrownBy(()->configuration.controlledMigrations("automatic","validate")).hasMessageContaining("validate or migrate");
    }
    @Test void rejectsHibernateSchemaWrites() {
        for(String mode:new String[]{"update","create","create-drop","none"})
            assertThatThrownBy(()->configuration.controlledMigrations("validate",mode)).hasMessageContaining("Hibernate must use");
    }
    @Test void rejectsAutomaticBaseline() {
        var flyway=mock(Flyway.class); var settings=mock(Configuration.class);
        when(flyway.getConfiguration()).thenReturn(settings);
        when(settings.isBaselineOnMigrate()).thenReturn(true);
        assertThatThrownBy(()->configuration.controlledMigrations("migrate","validate").migrate(flyway)).hasMessageContaining("prohibited");
        verify(flyway,never()).migrate();
    }
    @Test void rejectsCleanEnabled() {
        var flyway=mock(Flyway.class); var settings=mock(Configuration.class);
        when(flyway.getConfiguration()).thenReturn(settings);
        when(settings.isCleanDisabled()).thenReturn(false);
        assertThatThrownBy(()->configuration.controlledMigrations("migrate","validate").migrate(flyway)).hasMessageContaining("prohibited");
        verify(flyway,never()).migrate();
    }
    @Test void missingHistoryOnProductionStartupFailsWithoutAnyMigration() throws Exception {
        var flyway=mock(Flyway.class); var settings=mock(Configuration.class); var source=mock(DataSource.class);
        var connection=mock(Connection.class); var statement=mock(java.sql.Statement.class); var rows=mock(java.sql.ResultSet.class);
        when(flyway.getConfiguration()).thenReturn(settings); when(settings.isCleanDisabled()).thenReturn(true);
        when(settings.getDataSource()).thenReturn(source); when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement); when(statement.executeQuery(anyString())).thenReturn(rows);
        when(rows.next()).thenReturn(true); when(rows.getBoolean(1)).thenReturn(false);
        assertThatThrownBy(()->configuration.controlledMigrations("validate","validate").migrate(flyway)).hasMessageContaining("history is absent");
        verify(connection).setReadOnly(true);
        verify(flyway,never()).migrate(); verify(flyway,never()).baseline();
    }
}
