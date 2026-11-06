package app.tenancy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Ensures the preflight is read-only and executed on the Flyway target
 * datasource BEFORE Flyway has a chance to modify a remote database.
 */
class SupabaseSchemaCompatibilityGuardTest {

    private DataSource datasource(boolean collision) throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true);
        when(results.getBoolean(1)).thenReturn(collision);
        return source;
    }

    @Test
    void cleanFlywayTargetPassesReadonlyCheck() throws Exception {
        DataSource source = datasource(false);
        assertDoesNotThrow(() ->
                SupabaseSchemaCompatibilityGuard.requireFlywayCompatibleDatabase(source));
        Connection connection = source.getConnection();
        verify(connection).prepareStatement(contains("stockos_private"));
        verify(connection, never()).createStatement();
        verify(connection, never()).prepareCall(anyString());
    }

    @Test
    void existingStockosPrivateSaasSchemaIsRejected() throws Exception {
        DataSource source = datasource(true);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SupabaseSchemaCompatibilityGuard.requireFlywayCompatibleDatabase(source));
        assertTrue(error.getMessage().contains("Incompatibilidade de schema"));
    }

    @Test
    void unavailableDatabaseIsRejected() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("Test connection unavailable"));
        assertThrows(IllegalStateException.class,
                () -> SupabaseSchemaCompatibilityGuard.requireFlywayCompatibleDatabase(source));
        assertThrows(IllegalStateException.class,
                () -> SupabaseSchemaCompatibilityGuard.requireFlywayCompatibleDatabase(null));
    }

    @Test
    void flywayNeverMigratesWhenCollisionDetected() throws Exception {
        DataSource source = datasource(true);
        Flyway flyway = mock(Flyway.class);
        Configuration cfg = mock(Configuration.class);
        when(flyway.getConfiguration()).thenReturn(cfg);
        when(cfg.getDataSource()).thenReturn(source);

        FlywayMigrationStrategy strategy = new SupabaseFlywaySeparation()
                .requireSeparateMigrationIdentity("migration", "test-password", "application");

        assertThrows(IllegalStateException.class, () -> strategy.migrate(flyway));
        verify(flyway, never()).migrate();
    }

    @Test
    void flywayMigratesOnlyAfterCleanPreflight() throws Exception {
        DataSource source = datasource(false);
        Flyway flyway = mock(Flyway.class);
        Configuration cfg = mock(Configuration.class);
        when(flyway.getConfiguration()).thenReturn(cfg);
        when(cfg.getDataSource()).thenReturn(source);

        FlywayMigrationStrategy strategy = new SupabaseFlywaySeparation()
                .requireSeparateMigrationIdentity("migration", "test-password", "application");
        strategy.migrate(flyway);
        verify(flyway, times(1)).migrate();
    }
}
