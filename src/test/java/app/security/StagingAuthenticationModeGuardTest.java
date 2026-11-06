package app.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StagingAuthenticationModeGuardTest {
    private final StagingAuthenticationModeGuard guard = new StagingAuthenticationModeGuard();

    private MockEnvironment staging(String mode, String url) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");
        env.setProperty("stockos.auth.mode", mode);
        env.setProperty("stockos.auth.supabase-url", url);
        env.setProperty("DB_URL", "jdbc:postgresql://db.internal:5432/stockos");
        env.setProperty("DB_USERNAME", "stockos_app");
        env.setProperty("DB_PASSWORD", "runtime-test-only");
        env.setProperty("stockos.migrations.mode", "external");
        env.setProperty("spring.flyway.enabled", "false");
        return env;
    }

    @Test
    void rejectsLegacyModeInStaging() {
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(staging("legacy", "https://example.supabase.co"),
                        mock(SpringApplication.class)));
    }

    @Test
    void rejectsEmptySupabaseUrlInStaging() {
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(staging("supabase", ""),
                        mock(SpringApplication.class)));
    }

    @Test
    void permitsExplicitSupabaseConfig() {
        assertDoesNotThrow(() -> guard.postProcessEnvironment(
                staging("supabase", "https://example.supabase.co"), mock(SpringApplication.class)));
    }

    @Test
    void stagingRejectsMissingRuntimeOrMigrationCredentials() {
        for (String missing : new String[]{"DB_URL", "DB_USERNAME", "DB_PASSWORD"}) {
            MockEnvironment env = staging("supabase", "https://example.supabase.co");
            env.setProperty(missing, "");
            assertThrows(IllegalStateException.class,
                    () -> guard.postProcessEnvironment(env, mock(SpringApplication.class)),
                    "Must reject missing " + missing);
        }
    }

    @Test
    void stagingRejectsLocalDatabaseAndSharedOrAdminIdentity() {
        MockEnvironment local = staging("supabase", "https://example.supabase.co");
        local.setProperty("DB_URL", "jdbc:postgresql://localhost:5432/stockos");
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(local, mock(SpringApplication.class)));

        for (String runtime : new String[]{"stockos_migrator", "postgres", "stockos_local"}) {
            MockEnvironment env = staging("supabase", "https://example.supabase.co");
            env.setProperty("DB_USERNAME", runtime);
            assertThrows(IllegalStateException.class,
                    () -> guard.postProcessEnvironment(env, mock(SpringApplication.class)),
                    "Must reject runtime DB user " + runtime);
        }
    }

    @Test
    void stagingRejectsFlywayInApplicationAndMigrationSecrets() {
        MockEnvironment flywayEnabled = staging("supabase", "https://example.supabase.co");
        flywayEnabled.setProperty("spring.flyway.enabled", "true");
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(flywayEnabled, mock(SpringApplication.class)));

        MockEnvironment embedded = staging("supabase", "https://example.supabase.co");
        embedded.setProperty("stockos.migrations.mode", "embedded");
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(embedded, mock(SpringApplication.class)));

        for (String secret : new String[]{"SPRING_FLYWAY_USER",
                "SPRING_FLYWAY_PASSWORD", "SPRING_FLYWAY_URL"}) {
            MockEnvironment environment = staging("supabase", "https://example.supabase.co");
            environment.setProperty(secret, "should-not-be-present");
            assertThrows(IllegalStateException.class,
                    () -> guard.postProcessEnvironment(environment, mock(SpringApplication.class)));
        }
    }

    @Test
    void preservesLocalLegacyDevelopment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        env.setProperty("stockos.auth.mode", "legacy");
        assertDoesNotThrow(() ->
                guard.postProcessEnvironment(env, mock(SpringApplication.class)));
    }
}
