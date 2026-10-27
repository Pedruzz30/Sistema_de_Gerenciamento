package controller;

import app.StagingSupabaseSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only the verified PUBLIC Supabase URL and publishable key may reach browsers. */
@RestController
public class SaasPublicConfigController {
    private final StagingSupabaseSettings settings;

    public SaasPublicConfigController(StagingSupabaseSettings settings) {
        this.settings = settings;
    }

    @GetMapping("/api/saas/config")
    public PublicConfig config() {
        return new PublicConfig(settings.projectUrl(), settings.publishableKey());
    }

    public record PublicConfig(String supabaseUrl, String publishableKey) {}
}
