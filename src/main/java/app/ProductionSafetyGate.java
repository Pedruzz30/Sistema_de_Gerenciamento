package app;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Arrays;

/** Blocks unsafe deployment while tenant isolation and onboarding are unfinished. */
@Configuration
public class ProductionSafetyGate {
    private final String mode;
    private final String profiles;
    private final String issuer;
    private final String bindAddress;

    public ProductionSafetyGate(
            @Value("${stockos.security.mode:legacy-dev}") String mode,
            @Value("${spring.profiles.active:}") String profiles,
            @Value("${stockos.security.supabase-issuer:}") String issuer,
            @Value("${server.address:127.0.0.1}") String bindAddress) {
        this.mode = mode;
        this.profiles = profiles;
        this.issuer = issuer;
        this.bindAddress = bindAddress;
    }

    @PostConstruct
    public void verify() {
        boolean prod = Arrays.stream(profiles.split(","))
                .map(String::trim)
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
        if (prod) {
            throw new IllegalStateException("StockOS production remains blocked until verified "
                    + "JWT authorization AND company isolation are implemented and tested.");
        }

        if ("legacy-dev".equals(mode)) {
            if (!("127.0.0.1".equals(bindAddress) || "localhost".equalsIgnoreCase(bindAddress)
                    || "::1".equals(bindAddress))) {
                throw new IllegalStateException("Legacy authentication cannot be bound to a public interface.");
            }
            return;
        }
        if (!"supabase-jwt".equals(mode)) {
            throw new IllegalStateException("Unknown StockOS security mode: " + mode);
        }
        try {
            URI uri = URI.create(issuer);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !uri.getHost().endsWith(".supabase.co")
                    || !uri.getPath().replaceAll("/+$", "").endsWith("/auth/v1")
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("invalid issuer");
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Configure STOCKOS_SUPABASE_AUTH_ISSUER as a real Supabase HTTPS Auth issuer.", ex);
        }
    }
}
