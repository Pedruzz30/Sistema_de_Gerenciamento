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
    void preservesLocalLegacyDevelopment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        env.setProperty("stockos.auth.mode", "legacy");
        assertDoesNotThrow(() ->
                guard.postProcessEnvironment(env, mock(SpringApplication.class)));
    }
}
