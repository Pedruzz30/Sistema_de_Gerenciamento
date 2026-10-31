
package app.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import repository.UsuarioRepository;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Explicit opt-in Supabase mode; production must never run in legacy mode. */
@Configuration
public class StockosSecurityConfig {

    @Bean
    public SecurityFilterChain stockosSecurityFilterChain(
            HttpSecurity http,
            UsuarioRepository users,
            @Value("${stockos.auth.mode:legacy}") String mode,
            @Value("${stockos.auth.cors-origin:}") String corsOrigin) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if ("legacy".equals(mode)) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
        if (!"supabase".equals(mode)) {
            throw new IllegalStateException("STOCKOS_AUTH_MODE invalido: use legacy ou supabase.");
        }

        if (corsOrigin != null && !corsOrigin.isBlank()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of(corsOrigin.trim()));
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
            cors.setMaxAge(3600L);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/**", cors);
            http.cors(cfg -> cfg.configurationSource(source));
        }

        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/config").permitAll()
                        .requestMatchers("/api/auth/login").denyAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .addFilterAfter(new LinkedStockosUserFilter(users), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
    public JwtDecoder supabaseJwtDecoder(@Value("${stockos.auth.supabase-url:}") String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalStateException("Configure STOCKOS_SUPABASE_URL antes de habilitar Supabase Auth.");
        }
        URI url = URI.create(rawUrl.trim());
        boolean local = "localhost".equalsIgnoreCase(url.getHost()) || "127.0.0.1".equals(url.getHost());
        if ((!local && !"https".equalsIgnoreCase(url.getScheme()))
                || (local && !List.of("http", "https").contains(url.getScheme()))
                || url.getQuery() != null || url.getFragment() != null
                || url.getHost() == null || (url.getPath() != null && !url.getPath().isBlank() && !"/".equals(url.getPath()))) {
            throw new IllegalStateException("STOCKOS_SUPABASE_URL invalida; use a URL raiz HTTPS do projeto.");
        }
        String root = rawUrl.trim().replaceAll("/+$", "");
        String issuer = root + "/auth/v1";
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> claimsValidator = jwt -> {
            boolean audienceValid = jwt.getAudience().contains("authenticated");
            boolean roleValid = "authenticated".equals(jwt.getClaimAsString("role"));
            boolean subjectValid;
            try {
                UUID.fromString(jwt.getSubject());
                subjectValid = true;
            } catch (RuntimeException ignored) {
                subjectValid = false;
            }
            return audienceValid && roleValid && subjectValid
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "Supabase JWT with authenticated role and UUID subject required.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), claimsValidator));
        // HS256 tokens intentionally unsupported. Configure asymmetric signing
        // keys in Supabase Auth and never put the JWT secret in this service.
        return decoder;
    }
}
