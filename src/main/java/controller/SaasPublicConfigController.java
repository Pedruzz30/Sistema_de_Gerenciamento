package controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes only the public Supabase project URL and publishable key. */
@RestController
public class SaasPublicConfigController {
    @Value("${stockos.supabase.url:}") private String url;
    @Value("${stockos.supabase.publishable-key:}") private String publishableKey;

    @GetMapping("/api/saas/config")
    public PublicConfig config() {
        return new PublicConfig(url, publishableKey);
    }

    public record PublicConfig(String supabaseUrl, String publishableKey) {}
}
