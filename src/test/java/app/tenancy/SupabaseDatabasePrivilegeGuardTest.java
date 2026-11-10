package app.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupabaseDatabasePrivilegeGuardTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @SuppressWarnings("unchecked")
    private void mockDatabaseRole(boolean[] permissions) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        for (int i = 0; i < permissions.length; i++) {
            when(rs.getBoolean(i + 1)).thenReturn(permissions[i]);
        }
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenAnswer(
                invocation -> ((ResultSetExtractor<Boolean>) invocation.getArgument(1)).extractData(rs));
    }

    @Test
    void contaSemPoderesAdministrativosEhAceita() throws Exception {
        mockDatabaseRole(new boolean[7]);
        assertDoesNotThrow(() -> new SupabaseDatabasePrivilegeGuard(jdbc)
                .run(mock(ApplicationArguments.class)));
    }

    @Test
    void rejeitaSuperusuarioCriacaoOuDonoDeTabelas() throws Exception {
        for (int index = 0; index < 7; index++) {
            reset(jdbc);
            boolean[] permissions = new boolean[7];
            permissions[index] = true;
            mockDatabaseRole(permissions);
            assertThrows(IllegalStateException.class,
                    () -> new SupabaseDatabasePrivilegeGuard(jdbc)
                            .run(mock(ApplicationArguments.class)));
        }
    }

    @Test
    void rejeitaContaNaoEncontrada() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenAnswer(
                invocation -> ((ResultSetExtractor<Boolean>) invocation.getArgument(1)).extractData(rs));
        assertThrows(IllegalStateException.class,
                () -> new SupabaseDatabasePrivilegeGuard(jdbc)
                        .run(mock(ApplicationArguments.class)));
    }

    @Test
    void exigeIdentidadeDistintaDeMigracao() {
        SupabaseFlywaySeparation config = new SupabaseFlywaySeparation();
        assertThrows(IllegalStateException.class,
                () -> config.requireSeparateMigrationIdentity("", "password", "app"));
        assertThrows(IllegalStateException.class,
                () -> config.requireSeparateMigrationIdentity("app", "password", "app"));
        assertThrows(IllegalStateException.class,
                () -> config.requireSeparateMigrationIdentity("migration", "", "app"));
        assertNotNull(config.requireSeparateMigrationIdentity("migration", "password", "app"));
    }
}
