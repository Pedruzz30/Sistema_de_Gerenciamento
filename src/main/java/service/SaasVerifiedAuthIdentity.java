package service;

import app.StagingSupabaseSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Verify the authenticated caller against Supabase Auth /auth/v1/user.
 * Never trust email/user_metadata from browser or JWT as proof of confirmation.
 */
@Service
public class SaasVerifiedAuthIdentity {
    private final StagingSupabaseSettings config;
    private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public SaasVerifiedAuthIdentity(StagingSupabaseSettings config,ObjectMapper json){
        this.config=config;this.json=json;
    }

    public String confirmedEmail(UUID expectedUser) {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(!(auth instanceof JwtAuthenticationToken token) || !auth.isAuthenticated()
                || !expectedUser.toString().equals(token.getToken().getSubject()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sessao JWT invalida");
        if(config.projectUrl()==null || !config.projectUrl().startsWith("https://")
                || config.publishableKey()==null || !config.publishableKey().startsWith("sb_publishable_"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Auth nao configurado");
        try {
            HttpRequest request=HttpRequest.newBuilder(
                URI.create(config.projectUrl().replaceAll("/+$","")+"/auth/v1/user"))
                .timeout(Duration.ofSeconds(7))
                .header("apikey",config.publishableKey())
                .header("Authorization","Bearer "+token.getToken().getTokenValue())
                .GET().build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==401 || response.statusCode()==403)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sessao expirada");
            if(response.statusCode()!=200)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Auth indisponivel");
            JsonNode user=json.readTree(response.body());
            if(!expectedUser.toString().equals(user.path("id").asText())
                    || user.path("is_anonymous").asBoolean(false)
                    || user.path("email_confirmed_at").asText("").isBlank()
                    || user.path("email").asText("").isBlank())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Email nao confirmado");
            return user.path("email").asText().strip().toLowerCase(Locale.ROOT);
        } catch(ResponseStatusException ex) {
            throw ex;
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Auth indisponivel");
        } catch(Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Auth indisponivel");
        }
    }
}
