package support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDatabaseGuardTest {
    @Test
    void permitsOnlyDedicatedAndTemporaryDatabases() {
        assertDoesNotThrow(() -> TestDatabaseGuard.assertSafeJdbcUrl(
                "jdbc:postgresql://localhost:55432/sistema_gerenciamento_test"));
        assertDoesNotThrow(() -> TestDatabaseGuard.assertSafeJdbcUrl(
                "jdbc:postgresql://127.0.0.1:55432/sg_f46_empty_123?sslmode=disable"));
        assertDoesNotThrow(() -> TestDatabaseGuard.assertTemporaryDatabaseName("sg_f46_baseline_abc"));
    }

    @Test
    void rejectsProductionAndOtherNames() {
        reject("jdbc:postgresql://localhost:55432/sistema_gerenciamento");
        reject("jdbc:postgresql://localhost:55432/cliente");
        reject("jdbc:postgresql://localhost:55432/sg_f46_fake");
        assertThrows(IllegalStateException.class,
                () -> TestDatabaseGuard.assertTemporaryDatabaseName("sistema_gerenciamento"));
    }

    @Test
    void rejectsWrongHostsPortsAndMalformedUrls() {
        reject("jdbc:postgresql://localhost:5432/sistema_gerenciamento_test");
        reject("jdbc:postgresql://db.example.com:55432/sistema_gerenciamento_test");
        reject("jdbc:postgresql://127.0.0.2:55432/sistema_gerenciamento_test");
        reject("jdbc:postgresql://user:pass@localhost:55432/sistema_gerenciamento_test");
        reject("jdbc:postgresql://localhost:55432/%73istema_gerenciamento_test");
        reject("jdbc:postgresql://localhost:55432/sistema_gerenciamento_test/extra");
        reject(null);
        reject("");
    }

    @Test
    void listenerBlocksUnsafeSpringDatasource() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/sistema_gerenciamento"
        )));
        var event = new ApplicationEnvironmentPreparedEvent(
                new SpringApplication(Object.class), new String[0], environment);
        assertThrows(IllegalStateException.class,
                () -> new TestDatabaseGuard().onApplicationEvent(event));
    }

    private static void reject(String url) {
        assertThrows(IllegalStateException.class, () -> TestDatabaseGuard.assertSafeJdbcUrl(url));
    }
}
