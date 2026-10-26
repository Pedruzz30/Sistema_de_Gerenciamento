package app;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionSafetyGateTest {
    @Test
    void permitsLegacyModeOnlyOutsideProduction() {
        assertDoesNotThrow(() -> new ProductionSafetyGate("legacy-dev", "test").verify());
        assertDoesNotThrow(() -> new ProductionSafetyGate("legacy-dev", "").verify());
    }

    @Test
    void rejectsInsecureProductionProfiles() {
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("legacy-dev", "prod").verify());
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("legacy-dev", "dev, production").verify());
    }

    @Test
    void rejectsUnimplementedAuthModes() {
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("supabase-jwt", "dev").verify());
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGate("made-up", "dev").verify());
    }
}
