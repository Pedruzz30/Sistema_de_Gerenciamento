package app;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real ES256 signature verification using the production decoder with local JWKS. */
class SupabaseJwtEs256SignatureTest {
    private static final String ISSUER = "https://stockos-fixture.supabase.co/auth/v1";
    private static final String KID = "stockos-test-ec-p256";

    @Test
    void acceptsP256SupabaseJwtAndRejectsForgeryWrongClaimsAndSymmetricJwt() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        ECKey key = new ECKey.Builder(Curve.P_256, (ECPublicKey) pair.getPublic())
                .privateKey((ECPrivateKey) pair.getPrivate())
                .keyID(KID)
                .algorithm(JWSAlgorithm.ES256)
                .build();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            byte[] data = new JWKSet(key.toPublicJWK()).toString()
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, data.length);
            try (var out = exchange.getResponseBody()) {
                out.write(data);
            }
        });
        server.start();
        try {
            JwtDecoder decoder = StockOsSecurityConfiguration.buildJwtDecoder(
                    ISSUER, "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
            String legit = signed(key, ISSUER, "authenticated",
                    List.of("authenticated"), Instant.now().plusSeconds(300));
            assertEquals(ISSUER, decoder.decode(legit).getIssuer().toString());

            String[] parts = legit.split("\\.");
            String segment = parts[1];
            char changed = segment.charAt(5) == 'A' ? 'B' : 'A';
            String forged = parts[0] + "." + segment.substring(0, 5) + changed
                    + segment.substring(6) + "." + parts[2];
            assertThrows(JwtException.class, () -> decoder.decode(forged));
            assertThrows(JwtException.class, () -> decoder.decode(
                    signed(key, "https://other.supabase.co/auth/v1", "authenticated",
                            List.of("authenticated"), Instant.now().plusSeconds(300))));
            assertThrows(JwtException.class, () -> decoder.decode(
                    signed(key, ISSUER, "service_role",
                            List.of("authenticated"), Instant.now().plusSeconds(300))));
            assertThrows(JwtException.class, () -> decoder.decode(
                    signed(key, ISSUER, "authenticated",
                            List.of("authenticated"), Instant.now().minusSeconds(120))));

            // Legacy HS256 must never be trusted by the JWKS-based StockOS backend,
            // even with otherwise well-formed claims.
            SignedJWT symmetric = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    claims(ISSUER, "authenticated", List.of("authenticated"),
                            Instant.now().plusSeconds(300)));
            symmetric.sign(new MACSigner(new byte[32]));
            assertThrows(JwtException.class, () -> decoder.decode(symmetric.serialize()));
        } finally {
            server.stop(0);
        }
    }

    private static String signed(ECKey key, String issuer, String role,
            List<String> audience, Instant expires) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(KID).type(JOSEObjectType.JWT).build(),
                claims(issuer, role, audience, expires));
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }

    private static JWTClaimsSet claims(String issuer, String role,
            List<String> audience, Instant expires) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .audience(audience)
                .claim("role", role)
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(expires))
                .build();
    }
}
