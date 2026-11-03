package app.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real RS256 signatures and a disposable localhost JWKS HTTP endpoint.
 * No Supabase credentials, remote network or database required.
 * The HTTP security/membership path is exercised in a separate MVC test.
 */
class SupabaseJwtCryptographicVerificationTest {
    private HttpServer server;
    private RSAKey trusted;
    private String issuer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        trusted = rsaKey("test-key");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String jwks = new JWKSet(trusted.toPublicJWK()).toString();
        server.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
            byte[] bytes = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        String root = "http://127.0.0.1:" + server.getAddress().getPort();
        issuer = root + "/auth/v1";
        decoder = new StockosSecurityConfig().supabaseJwtDecoder(root);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void verifiedSignatureAndClaimsAreAccepted() throws Exception {
        UUID subject = UUID.randomUUID();
        String token = token(trusted, issuer, "authenticated", "authenticated",
                subject.toString(), Instant.now().plusSeconds(300));
        var verified = decoder.decode(token);
        assertEquals(subject.toString(), verified.getSubject());
        assertEquals(issuer, verified.getIssuer().toString());
    }

    @Test
    void forgedSignatureWithTrustedKeyIdIsRejected() throws Exception {
        String forged = token(rsaKey("test-key"), issuer, "authenticated", "authenticated",
                UUID.randomUUID().toString(), Instant.now().plusSeconds(300));
        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }

    @Test
    void badIssuerAudienceRoleSubjectAndExpiryAreRejected() throws Exception {
        String subject = UUID.randomUUID().toString();
        Instant future = Instant.now().plusSeconds(300);
        String[] rejected = {
                token(trusted, "https://another-project.supabase.co/auth/v1",
                        "authenticated", "authenticated", subject, future),
                token(trusted, issuer, "anon", "authenticated", subject, future),
                token(trusted, issuer, "authenticated", "service_role", subject, future),
                token(trusted, issuer, "authenticated", "authenticated", "not-a-uuid", future),
                token(trusted, issuer, "authenticated", "authenticated",
                        subject, Instant.now().minusSeconds(300))
        };
        for (String invalid : rejected) {
            assertThrows(JwtException.class, () -> decoder.decode(invalid));
        }
    }

    private static RSAKey rsaKey(String kid) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(kid).build();
    }

    private static String token(RSAKey signingKey, String issuer,
                                String audience, String role,
                                String subject, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer).subject(subject).audience(audience)
                .claim("role", role)
                .issueTime(Date.from(Instant.now().minusSeconds(20)))
                .expirationTime(Date.from(expiresAt)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
