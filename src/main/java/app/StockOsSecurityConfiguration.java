package app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT validation lives on the server. The legacy authentication endpoint is
 * denied in JWT mode; the client cannot select a user with X-User-RU.
 */
@Configuration
public class StockOsSecurityConfiguration {
    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http,
            @Value("${stockos.security.mode:legacy-dev}") String mode) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if ("supabase-jwt".equals(mode)) {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/saas/config").permitAll()
                    .requestMatchers("/api/auth/login").denyAll()
                    // Only SaaS endpoints designed with tenant membership checks are enabled.
                    // Legacy APIs still trust caller-controlled filialId; keep them unreachable.
                    .requestMatchers("/api/auth/me", "/api/saas/**", "/api/admin/stockos/**").authenticated()
                    .requestMatchers("/api/**").denyAll()
                    .anyRequest().permitAll());
            http.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        } else {
            // Strictly local dev only. ProductionSafetyGate rejects public binding.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "stockos.security.mode", havingValue = "supabase-jwt")
    public JwtDecoder supabaseJwtDecoder(
            @Value("${stockos.security.supabase-issuer:}") String rawIssuer) {
        String issuer = rawIssuer.replaceAll("/+$", "");
        // New StockOS Supabase project must use an asymmetric signing key;
        // legacy HS256 secrets cannot be verified through JWKS.
        return buildJwtDecoder(issuer, issuer + "/.well-known/jwks.json");
    }

    /**
     * Same production decoder and claim validators, with the JWKS location
     * injectable only for isolated cryptographic tests. Production always
     * derives the JWKS URL from its validated Supabase issuer.
     */
    static NimbusJwtDecoder buildJwtDecoder(String issuer, String jwksUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();

        OAuth2TokenValidator<Jwt> authenticatedAudienceAndRole = token ->
                token.getAudience().contains("authenticated")
                        && "authenticated".equals(token.getClaimAsString("role"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "JWT audience or role is not authenticated", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                authenticatedAudienceAndRole));
        return decoder;
    }
}
