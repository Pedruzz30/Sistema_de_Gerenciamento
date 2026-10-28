package support;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-fast safety net for the automated test suite.
 *
 * <p>Refuses to let ANY Spring context started during tests connect to the
 * production database ("sistema_gerenciamento"). This listener reacts to
 * {@link ApplicationEnvironmentPreparedEvent}, which fires after the
 * environment (including {@code application.properties},
 * {@code application-test-local.properties} and any {@code --spring.datasource.url=}
 * command-line override) has been fully resolved, but strictly before any
 * bean - DataSource, Flyway, EntityManagerFactory - is created. If the
 * resolved JDBC URL points at the production database, this throws
 * immediately and the context never finishes starting, so no test can write
 * a single row to production even if some property is misconfigured.</p>
 *
 * <p>Registered exclusively via {@code src/test/resources/META-INF/spring.factories},
 * so it only ever loads on the test classpath and has zero effect on the
 * real, running application.</p>
 */
public class TestDatabaseGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    /** The one and only production database. Tests must never target this. */
    private static final String FORBIDDEN_DATABASE = "sistema_gerenciamento";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            return;
        }

        String databaseName = extractDatabaseName(url);
        if (FORBIDDEN_DATABASE.equalsIgnoreCase(databaseName)) {
            throw new IllegalStateException(
                    "TESTE BLOQUEADO: a configuracao resolvida aponta para o banco de PRODUCAO '"
                            + FORBIDDEN_DATABASE + "'. Testes automatizados nunca podem escrever nesse banco. "
                            + "Use um banco dedicado (ex.: sistema_gerenciamento_test) definido via a variavel "
                            + "de ambiente DB_URL ou em src/test/resources/application-test-local.properties. "
                            + "URL JDBC recebida: " + url);
        }
    }

    /**
     * Extracts the database name from a JDBC URL of the form
     * {@code jdbc:postgresql://host:port/dbname?query}. Returns {@code null}
     * if the URL does not match the expected shape (in which case the guard
     * simply does not match anything and lets Spring's own validation take
     * over).
     */
    private static String extractDatabaseName(String jdbcUrl) {
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        String afterScheme = jdbcUrl.substring(schemeEnd + 3);
        int pathStart = afterScheme.indexOf('/');
        if (pathStart < 0) {
            return null;
        }
        String path = afterScheme.substring(pathStart + 1);
        int queryStart = path.indexOf('?');
        return queryStart >= 0 ? path.substring(0, queryStart) : path;
    }

    /**
     * Must run AFTER Spring Boot's own {@code EnvironmentPostProcessor}
     * machinery (which loads application*.properties and resolves
     * {@code ${DB_URL:...}} placeholders) has had a chance to act on this
     * same event. Using the lowest precedence guarantees that by the time
     * this listener runs, spring.datasource.url already holds its final,
     * fully-resolved value.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
