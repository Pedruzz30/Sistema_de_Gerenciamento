package app.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

/**
 * Runs while Boot is preparing configuration, BEFORE the application context,
 * Flyway or HTTP server starts. Staging must never silently inherit the
 * legacy no-authentication fallback from application.properties.
 */
public final class StagingAuthenticationModeGuard implements EnvironmentPostProcessor, Ordered {
    @Override
    public int getOrder() {
        // After Spring Boot ConfigData has loaded application-staging.properties.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        boolean staging = Arrays.asList(env.getActiveProfiles()).contains("staging");
        if (!staging) {
            return;
        }
        String mode = env.getProperty("stockos.auth.mode", "").trim();
        if (!"supabase".equals(mode)) {
            throw new IllegalStateException(
                    "Perfil staging requer stockos.auth.mode=supabase; "
                    + "autenticacao legada indisponivel antes do startup.");
        }
        String supabaseUrl = env.getProperty("stockos.auth.supabase-url", "").trim();
        if (supabaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Perfil staging requer STOCKOS_SUPABASE_URL antes do startup.");
        }
    }
}
