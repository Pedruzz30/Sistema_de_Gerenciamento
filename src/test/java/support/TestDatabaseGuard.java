package support;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Restricts Spring test contexts and explicitly checked JDBC calls to local test PostgreSQL. */
public final class TestDatabaseGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
    public static final String DEFAULT_TEST_JDBC_URL =
            "jdbc:postgresql://localhost:55432/sistema_gerenciamento_test";
    private static final Set<String> HOSTS = Set.of("localhost", "127.0.0.1");
    private static final Pattern TEMP_DB = Pattern.compile("sg_f46_(?:empty|baseline|bad)_[a-z0-9]+");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        assertSafeJdbcUrl(event.getEnvironment().getProperty("spring.datasource.url"));
    }

    public static void assertSafeJdbcUrl(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw unsafe("Expected a PostgreSQL test JDBC URL.");
        }
        final URI uri;
        try {
            uri = URI.create(url.substring("jdbc:".length()));
        } catch (IllegalArgumentException ex) {
            throw unsafe("Invalid JDBC URL.");
        }
        String host = uri.getHost(), path = uri.getPath();
        if (!"postgresql".equalsIgnoreCase(uri.getScheme()) ||
                host == null || !HOSTS.contains(host.toLowerCase(Locale.ROOT)) ||
                uri.getPort() != 55432 || uri.getUserInfo() != null ||
                uri.getFragment() != null || path == null ||
                !path.startsWith("/") || path.indexOf('/', 1) >= 0) {
            throw unsafe("Tests may connect only to localhost:55432.");
        }
        String database = path.substring(1);
        if (!"sistema_gerenciamento_test".equals(database) && !TEMP_DB.matcher(database).matches()) {
            throw unsafe("Database name is not allowlisted for tests.");
        }
    }

    public static void assertTemporaryDatabaseName(String database) {
        if (database == null || !TEMP_DB.matcher(database).matches()) {
            throw unsafe("Refusing CREATE/DROP DATABASE on a non-temporary name.");
        }
    }

    private static IllegalStateException unsafe(String message) {
        return new IllegalStateException("TEST DATABASE SAFETY BLOCK: " + message);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
