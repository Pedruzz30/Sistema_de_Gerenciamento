package app;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Fail-closed configuration for an isolated Supabase Auth staging project.
 * Publishable keys are intended for browsers. Secret/service-role keys are not.
 */
@Component
public class StagingSupabaseSettings {
    private final String mode;
    private final String issuer;
    private final String projectUrl;
    private final String publishableKey;

    public StagingSupabaseSettings(
            @Value("${stockos.security.mode:legacy-dev}") String mode,
            @Value("${stockos.security.supabase-issuer:}") String issuer,
            @Value("${stockos.supabase.url:}") String projectUrl,
            @Value("${stockos.supabase.publishable-key:}") String publishableKey) {
        this.mode=mode;
        this.issuer=issuer;
        this.projectUrl=projectUrl;
        this.publishableKey=publishableKey;
    }

    @PostConstruct
    public void validate() {
        // Refuse potentially elevated key material in this public endpoint,
        // even if the app is temporarily running in legacy local mode.
        if(!publishableKey.isBlank() && !publishableKey.startsWith("sb_publishable_"))
            throw new IllegalStateException("Only Supabase publishable keys may be used by browser config.");
        if(!"supabase-jwt".equals(mode))return;
        try {
            URI project=URI.create(projectUrl);
            URI tokenIssuer=URI.create(issuer);
            String host=project.getHost();
            if(!"https".equalsIgnoreCase(project.getScheme())
                    || host==null || !host.toLowerCase(Locale.ROOT).matches("[a-z0-9-]+\\.supabase\\.co")
                    || project.getPort()!=-1 || project.getUserInfo()!=null
                    || project.getQuery()!=null || project.getFragment()!=null
                    || (!project.getPath().isEmpty() && !"/".equals(project.getPath()))
                    || !issuer.equals(projectUrl.replaceAll("/+$","")+"/auth/v1")
                    || !project.getHost().equalsIgnoreCase(tokenIssuer.getHost())
                    || !publishableKey.startsWith("sb_publishable_")
                    || publishableKey.length()<25) {
                throw new IllegalArgumentException("inconsistent public Supabase config");
            }
        } catch(RuntimeException ex) {
            throw new IllegalStateException(
              "JWT staging requires matching HTTPS project URL, issuer and a PUBLIC publishable key.",ex);
        }
    }

    public String projectUrl() {return projectUrl;}
    public String publishableKey() {return publishableKey;}
}
