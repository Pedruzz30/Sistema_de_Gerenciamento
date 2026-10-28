package app;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionSafetyGateTest {
    private static final String ISSUER = "https://example-project.supabase.co/auth/v1";

    @Test
    void legacyAllowedOnlyOnLoopback() {
        assertDoesNotThrow(() -> new ProductionSafetyGate("legacy-dev", "test", "", "127.0.0.1").verify());
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("legacy-dev", "dev", "", "0.0.0.0").verify());
    }

    @Test
    void productionAlwaysBlockedUntilTenantSecurityIsReady() {
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("legacy-dev", "prod", "", "127.0.0.1").verify());
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("supabase-jwt", "dev,production", ISSUER, "127.0.0.1").verify());
    }

    @Test
    void jwtModeRequiresValidIssuer() {
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("supabase-jwt", "test", "", "127.0.0.1").verify());
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("supabase-jwt", "test", "http://host/auth/v1", "127.0.0.1").verify());
        assertDoesNotThrow(() -> new ProductionSafetyGate("supabase-jwt", "test", ISSUER, "127.0.0.1").verify());
    }

    @Test
    void unknownModeIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("fake", "test", "", "127.0.0.1").verify());
    }
}
