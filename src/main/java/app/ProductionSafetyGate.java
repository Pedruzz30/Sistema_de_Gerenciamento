package app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

/**
 * Prevents accidentally exposing the legacy X-User-RU login over the internet.
 * This repository does NOT yet implement verified Supabase JWT authorization.
 */
@Configuration
public class ProductionSafetyGate {
    private final String mode;
    private final String profiles;

    public ProductionSafetyGate(
            @Value("${stockos.security.mode:legacy-dev}") String mode,
            @Value("${spring.profiles.active:}") String profiles) {
        this.mode = mode;
        this.profiles = profiles;
    }

    @PostConstruct
    public void verify() {
        boolean prod = Arrays.stream(profiles.split(","))
                .map(String::trim)
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
        if (!"legacy-dev".equals(mode)) {
            throw new IllegalStateException("StockOS security mode '" + mode
                    + "' is not implemented. JWT authentication must be integrated before deployment.");
        }
        if (prod) {
            throw new IllegalStateException("StockOS cannot start with the insecure legacy X-User-RU "
                    + "authentication in a production profile. See docs/saas-migration-foundation.md.");
        }
    }
}
