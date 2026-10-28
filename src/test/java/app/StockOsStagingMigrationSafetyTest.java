package app;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/** Prevent staging from accidentally replaying legacy Flyway migrations. */
class StockOsStagingMigrationSafetyTest {
    @Test
    void stagingDisablesFlywayAndKeepsHibernateValidation() throws Exception {
        var properties=new Properties();
        try(InputStream in=getClass().getClassLoader()
                .getResourceAsStream("application-staging.properties")) {
            assertNotNull(in,"Missing staging safety profile");
            properties.load(in);
        }
        assertEquals("false",properties.getProperty("spring.flyway.enabled"));
        assertEquals("validate",properties.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false",properties.getProperty("stockos.seed.demo-menu.enabled"));
    }
}
