package saas;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Isolated SaaS entrypoint never allows legacy-dev authentication, even when
 * a developer forgets to set an environment variable or profile.
 */
@Component
public class SaasStagingBootGuard {
    private final String profiles;
    private final String mode;
    public SaasStagingBootGuard(@Value("${spring.profiles.active:}") String profiles,
            @Value("${stockos.security.mode:legacy-dev}") String mode) {
        this.profiles=profiles;
        this.mode=mode;
    }
    @PostConstruct
    public void verify() {
        if (!java.util.Arrays.stream(profiles.split(","))
                .map(String::trim).anyMatch("staging"::equalsIgnoreCase)
                || !"supabase-jwt".equals(mode)) {
            throw new IllegalStateException(
                "StockOS SaaS entrypoint requires staging profile and supabase-jwt mode.");
        }
    }
}
