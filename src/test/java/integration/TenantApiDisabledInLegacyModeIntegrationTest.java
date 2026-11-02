package integration;

import app.Main;
import app.tenancy.LegacyApiIsolationGate;
import controller.TenantCatalogController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A legacy-only deployment must never publish tenant API endpoints
 * without the JWT verified-identity filter from the Supabase PR.
 */
@SpringBootTest(classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "stockos.auth.mode=legacy")
class TenantApiDisabledInLegacyModeIntegrationTest {
    @Autowired ApplicationContext context;

    @Test
    void semSupabaseNaoRegistraApiTenantAwareNemGateCondicional() {
        assertFalse(context.containsBeanDefinition("tenantCatalogController"));
        assertFalse(context.containsBeanDefinition("legacyApiIsolationGate"));
        assertFalse(context.getBeansOfType(TenantCatalogController.class).size() > 0);
        assertFalse(context.getBeansOfType(LegacyApiIsolationGate.class).size() > 0);
    }
}
