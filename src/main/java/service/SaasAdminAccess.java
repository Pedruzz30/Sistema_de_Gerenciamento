package service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Explicit operator allowlist; customer PROPRIETARIO is never an administrator. */
@Service
public class SaasAdminAccess {
    private final boolean enabled;
    private final Set<UUID> admins;

    public SaasAdminAccess(@Value("${stockos.admin.enabled:false}") boolean enabled,
            @Value("${stockos.admin.auth-user-ids:}") String allowedUserIds) {
        this.enabled = enabled;
        this.admins = Arrays.stream(allowedUserIds.split(","))
            .map(String::trim).filter(v -> !v.isBlank())
            .map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }

    public UUID requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT administrativo obrigatorio");
        UUID subject;
        try { subject = UUID.fromString(jwt.getToken().getSubject()); }
        catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Identidade JWT invalida");
        }
        if (!enabled || !admins.contains(subject))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Acesso restrito");
        return subject;
    }
}
