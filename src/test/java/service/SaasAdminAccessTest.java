package service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SaasAdminAccessTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private void login(UUID id){
        Jwt token=Jwt.withTokenValue("test").header("alg","RS256").subject(id.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
            token,List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"))));
    }
    @Test void disabledRejectsEvenAllowlistedUser(){
        UUID user=UUID.randomUUID(); login(user);
        SaasAdminAccess access=new SaasAdminAccess(false,user.toString());
        assertEquals(HttpStatus.FORBIDDEN,
                     assertThrows(ResponseStatusException.class,access::requireAdmin).getStatusCode());
    }
    @Test void onlyAllowlistedJwtSubjectPasses(){
        UUID admin=UUID.randomUUID();SaasAdminAccess access=new SaasAdminAccess(true,admin.toString());
        login(UUID.randomUUID());
        assertEquals(HttpStatus.FORBIDDEN,
                     assertThrows(ResponseStatusException.class,access::requireAdmin).getStatusCode());
        login(admin);assertEquals(admin,access.requireAdmin());
    }
    @Test void missingTokenIsRejected(){
        SaasAdminAccess access=new SaasAdminAccess(true,UUID.randomUUID().toString());
        assertEquals(HttpStatus.UNAUTHORIZED,
                     assertThrows(ResponseStatusException.class,access::requireAdmin).getStatusCode());
    }
    @Test void malformedAllowlistRejectsStartup(){
        assertThrows(IllegalArgumentException.class,
                     ()->new SaasAdminAccess(true,"admin@example.com"));
    }
}
