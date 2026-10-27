package integration;

import app.Main;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fase46FlywayMigrationIntegrationTest {

    private static final String VERSAO_ATUAL_SCHEMA = "20261027.06";

    @Test
    void bancoVazioExecutaFlywayEHibernateValidate() throws Exception {
        DatabaseTarget target = criarBancoTemporario("empty");
        try {
            assertDoesNotThrow(() -> {
                try (ConfigurableApplicationContext ignored = iniciarAplicacao(target.url(), false)) {
                    // Contexto iniciado: Flyway migrou e Hibernate validou.
                }
            });

            try (Connection conn = DriverManager.getConnection(target.url(), target.user(), target.password());
                 Statement st = conn.createStatement()) {
                assertTrue(tabelaExiste(st, "vendas"));
                assertTrue(tabelaExiste(st, "venda_item_modificador_impactos"));
                assertTrue(tabelaExiste(st, "venda_item_partes"));
                assertTrue(tabelaExiste(st, "flyway_schema_history"));
                assertEquals(VERSAO_ATUAL_SCHEMA, versaoAtualFlyway(st));
            }
        } finally {
            removerBancoTemporario(target);
        }
    }

    @Test
    void bancoExistenteCompativelPodeSerBaselinedEValidado() throws Exception {
        DatabaseTarget target = criarBancoTemporario("baseline");
        try {
            Flyway.configure()
                    .dataSource(target.url(), target.user(), target.password())
                    .locations("classpath:db/migration")
                    .target("20260714.03")
                    .load()
                    .migrate();

            try (Connection conn = DriverManager.getConnection(target.url(), target.user(), target.password());
                 Statement st = conn.createStatement()) {
                st.execute("drop table flyway_schema_history");
            }

            assertDoesNotThrow(() -> {
                try (ConfigurableApplicationContext ignored = iniciarAplicacao(target.url(), true)) {
                    // Contexto iniciado: schema existente foi baselined e validado.
                }
            });

            try (Connection conn = DriverManager.getConnection(target.url(), target.user(), target.password());
                 Statement st = conn.createStatement()) {
                assertEquals(VERSAO_ATUAL_SCHEMA, versaoAtualFlyway(st));
                assertTrue(tabelaExiste(st, "venda_item_consumo_estornos"));
            }
        } finally {
            removerBancoTemporario(target);
        }
    }

    @Test
    void bancoIncompativelFalhaAntesDeValidarAplicacao() throws Exception {
        DatabaseTarget target = criarBancoTemporario("bad");
        try {
            try (Connection conn = DriverManager.getConnection(target.url(), target.user(), target.password());
                 Statement st = conn.createStatement()) {
                st.execute("create table produtos (id integer primary key)");
            }

            Exception erro = assertThrows(Exception.class, () -> {
                try (ConfigurableApplicationContext ignored = iniciarAplicacao(target.url(), true)) {
                    // Deve falhar durante Flyway.
                }
            });
            assertTrue(erro.getMessage() != null && !erro.getMessage().isBlank());
        } finally {
            removerBancoTemporario(target);
        }
    }

    private ConfigurableApplicationContext iniciarAplicacao(String jdbcUrl, boolean baselineOnMigrate) {
        return new SpringApplicationBuilder(Main.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=" + dbUser(),
                        "--spring.datasource.password=" + dbPassword(),
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.baseline-on-migrate=" + baselineOnMigrate,
                        "--spring.flyway.baseline-version=20260714.03",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--stockos.schema-compatibility-runner.enabled=false",
                        "--spring.main.lazy-initialization=true",
                        "--spring.thymeleaf.check-template-location=false"
                );
    }

    private DatabaseTarget criarBancoTemporario(String sufixo) throws Exception {
        String dbName = ("sg_f46_" + sufixo + "_" + Long.toUnsignedString(System.nanoTime(), 36))
                .toLowerCase(Locale.ROOT);
        DatabaseTarget target = new DatabaseTarget(dbName, jdbcUrlForDatabase(dbName), dbUser(), dbPassword());
        try (Connection conn = DriverManager.getConnection(adminUrl(), target.user(), target.password());
             Statement st = conn.createStatement()) {
            st.execute("create database " + dbName);
        }
        return target;
    }

    private void removerBancoTemporario(DatabaseTarget target) throws Exception {
        try (Connection conn = DriverManager.getConnection(adminUrl(), target.user(), target.password());
             Statement st = conn.createStatement()) {
            st.execute("drop database if exists " + target.dbName() + " with (force)");
        }
    }

    private static boolean tabelaExiste(Statement st, String tabela) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "select exists (select 1 from information_schema.tables where table_schema = current_schema() and table_name = '" + tabela + "')")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private static String versaoAtualFlyway(Statement st) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "select version from flyway_schema_history where success = true order by installed_rank desc limit 1")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static String jdbcUrl() {
        // Default is the dedicated test database, never production. This URL
        // is only used as the admin connection point to CREATE/DROP the
        // throwaway sg_f46_* databases this test creates for itself; it must
        // still never default to "sistema_gerenciamento".
        return System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/sistema_gerenciamento_test");
    }

    private static String dbUser() {
        return System.getenv().getOrDefault("DB_USERNAME", "postgres");
    }

    private static String dbPassword() {
        String env = System.getenv("DB_PASSWORD");
        if (env != null) {
            return env;
        }
        Properties properties = propriedadesLocais();
        return properties.getProperty("DB_PASSWORD",
            properties.getProperty("spring.datasource.password", ""));
    }

    private static String adminUrl() {
        return jdbcUrl();
    }

    private static String jdbcUrlForDatabase(String dbName) {
        String raw = jdbcUrl();
        URI uri = URI.create(raw.substring("jdbc:".length()));
        String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
        return "jdbc:postgresql://" + uri.getAuthority() + "/" + dbName + query;
    }

    private record DatabaseTarget(String dbName, String url, String user, String password) {}

    private static Properties propriedadesLocais() {
        Properties properties = new Properties();
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(
                java.nio.file.Path.of("src", "main", "resources", "application-local.properties"))) {
            properties.load(in);
        } catch (Exception ignored) {
            // application-local.properties e opcional.
        }
        return properties;
    }
}
