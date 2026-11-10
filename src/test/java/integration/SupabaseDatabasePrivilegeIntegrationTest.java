package integration;

import app.tenancy.SupabaseDatabasePrivilegeGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Executed only against disposable local stockos_test.
 * A separate login role is used to prove SQL-level permissions, rather than
 * merely mocking a privilege check. Does not connect to Supabase Cloud.
 */
class SupabaseDatabasePrivilegeIntegrationTest {
    @Test
    void usuarioRuntimeConsegueLerMasNaoConsegueCriarOuAlterarTabelas() throws Exception {
        String url = databaseUrl();
        String admin = System.getenv().getOrDefault("STOCKOS_TEST_DB_USERNAME", "stockos_local");
        String adminPassword = System.getenv("STOCKOS_TEST_DB_PASSWORD");
        assertNotNull(adminPassword, "Set STOCKOS_TEST_DB_PASSWORD for isolated local tests");
        assertFalse(adminPassword.isBlank());

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String role = "stockos_probe_app_" + suffix;
        String schema = "stockos_probe_" + suffix;
        String password = "probe_" + UUID.randomUUID().toString().replace("-", "");
        String runtimeUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;

        try (Connection owner = DriverManager.getConnection(url, admin, adminPassword);
             Statement st = owner.createStatement()) {
            try (ResultSet result = st.executeQuery("select 1")) {
                assertTrue(result.next(), "Database connection should return a row");
                assertEquals(1, result.getInt(1));
            }
            try {
                st.execute("create role " + role
                        + " login password '" + password + "'"
                        + " nosuperuser nocreatedb nocreaterole nobypassrls");
                st.execute("create schema " + schema);
                st.execute("create table " + schema + ".probe(id integer primary key, val integer not null)");
                st.execute("insert into " + schema + ".probe values (1, 7)");
                st.execute("grant connect on database stockos_test to " + role);
                st.execute("grant usage on schema " + schema + " to " + role);
                st.execute("grant select on " + schema + ".probe to " + role);

                DriverManagerDataSource runtime = new DriverManagerDataSource(runtimeUrl, role, password);
                JdbcTemplate jdbc = new JdbcTemplate(runtime);
                assertEquals(7, jdbc.queryForObject(
                        "select val from " + schema + ".probe where id = 1", Integer.class));
                assertDoesNotThrow(() -> new SupabaseDatabasePrivilegeGuard(jdbc)
                        .run(mock(ApplicationArguments.class)));

                try (Connection restricted = DriverManager.getConnection(runtimeUrl, role, password);
                     Statement limited = restricted.createStatement()) {
                    assertThrows(SQLException.class,
                            () -> limited.execute("create table " + schema + ".new_table(id int)"));
                    assertThrows(SQLException.class,
                            () -> limited.execute("alter table " + schema + ".probe add column stolen int"));
                    assertThrows(SQLException.class,
                            () -> limited.execute("update " + schema + ".probe set val = 999 where id = 1"));
                    try (ResultSet rs = limited.executeQuery("select val from " + schema + ".probe")) {
                        assertTrue(rs.next());
                        assertEquals(7, rs.getInt(1));
                    }
                }
            } finally {
                st.execute("drop schema if exists " + schema + " cascade");
                st.execute("drop owned by " + role);
                st.execute("drop role if exists " + role);
            }
        }
    }

    private static String databaseUrl() {
        String url = System.getenv().getOrDefault(
                "STOCKOS_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:5433/stockos_test");
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Expected isolated PostgreSQL test JDBC URL");
        }
        URI parsed = URI.create(url.substring("jdbc:".length()));
        boolean local = "127.0.0.1".equals(parsed.getHost())
                || "localhost".equalsIgnoreCase(parsed.getHost());
        if (!local || !"/stockos_test".equals(parsed.getPath())) {
            throw new IllegalStateException("Privilege integration tests require local stockos_test");
        }
        return url;
    }
}
