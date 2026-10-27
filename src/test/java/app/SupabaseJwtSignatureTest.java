package app;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the ACTUAL Nimbus decoder, signature, issuer and audience checks. */
class SupabaseJwtSignatureTest {
    private static final String ISSUER="https://stockos-fixture.supabase.co/auth/v1";
    private static final String KID="stockos-ci-rsa-001";

    @Test
    void jwksRejectsTamperingWrongIssuerExpiredAndWrongRole() throws Exception {
        var gen=KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        var keys=gen.generateKeyPair();
        var jwk=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)keys.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey)keys.getPrivate())
                .keyID(KID).algorithm(JWSAlgorithm.RS256).build();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/jwks",exchange->{
            byte[] data=new JWKSet(jwk.toPublicJWK()).toString()
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,data.length);
            try(var out=exchange.getResponseBody()){out.write(data);}
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try {
            JwtDecoder decoder=StockOsSecurityConfiguration.buildJwtDecoder(
                    ISSUER,"http://127.0.0.1:"+server.getAddress().getPort()+"/jwks");
            String valid=token(jwk,ISSUER,"authenticated",List.of("authenticated"),
                    Instant.now().plusSeconds(300));
            var parsed=decoder.decode(valid);
            assertEquals(ISSUER,parsed.getIssuer().toString());
            assertTrue(parsed.getAudience().contains("authenticated"));

            var parts=valid.split("\\.");
            // Swap a character in signed body but keep original signature.
            String payload=parts[1];
            char replacement=payload.charAt(5)=='A'?'B':'A';
            String forged=parts[0]+"."+payload.substring(0,5)+replacement+
                    payload.substring(6)+"."+parts[2];
            assertThrows(JwtException.class,()->decoder.decode(forged));
            assertThrows(JwtException.class,()->decoder.decode(
                    token(jwk,"https://evil.supabase.co/auth/v1",
                          "authenticated",List.of("authenticated"),Instant.now().plusSeconds(300))));
            assertThrows(JwtException.class,()->decoder.decode(
                    token(jwk,ISSUER,"authenticated",List.of("authenticated"),
                          Instant.now().minusSeconds(120))));
            assertThrows(JwtException.class,()->decoder.decode(
                    token(jwk,ISSUER,"service_role",List.of("authenticated"),
                          Instant.now().plusSeconds(300))));
            assertThrows(JwtException.class,()->decoder.decode(
                    token(jwk,ISSUER,"authenticated",List.of("anon"),
                          Instant.now().plusSeconds(300))));
        } finally {
            server.stop(0);
        }
    }

    private static String token(RSAKey key,String issuer,String role,
            List<String> audience,Instant expiresAt) throws Exception {
        var claims=new JWTClaimsSet.Builder()
            .issuer(issuer).subject(UUID.randomUUID().toString())
            .audience(audience)
            .claim("role",role)
            .issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(expiresAt))
            .build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                   .keyID(KID).type(JOSEObjectType.JWT).build(),claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
