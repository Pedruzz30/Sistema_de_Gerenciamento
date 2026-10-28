package integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import service.SaasRlsReadExecutor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against CI's isolated SaaS database as a REAL restricted login role,
 * never as postgres/owner, after private RLS policies and fixtures are loaded.
 */
@EnabledIfEnvironmentVariable(named="STOCKOS_SAAS_ONLY_DB_URL",
    matches="jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/stockos_ci_saas")
class SaasRlsReadExecutorPostgresIntegrationTest {
    private static JdbcTemplate jdbc;
    private static SaasRlsReadExecutor executor;
    private static DataSourceTransactionManager manager;
    private static final UUID A = UUID.fromString("aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa");
    private static final UUID B = UUID.fromString("bbbbbbbb-2222-4222-8222-bbbbbbbbbbbb");
    private static final UUID DISABLED = UUID.fromString("bbbbbbbb-3333-4333-8333-bbbbbbbbbbbb");

    @BeforeAll
    static void initialize() {
        String url = System.getenv("STOCKOS_SAAS_ONLY_DB_URL");
        assertEquals("jdbc:postgresql://127.0.0.1:5432/stockos_ci_saas",url);
        // CI-only role. Its credential is not used for any managed Supabase DB.
        var source = new DriverManagerDataSource(url, "stockos_ci_reader", "ci_reader_only");
        manager = new DataSourceTransactionManager(source);
        jdbc = new JdbcTemplate(source);
        executor = new SaasRlsReadExecutor(jdbc,manager);
    }

    private static void login(UUID id) {
        Jwt jwt=Jwt.withTokenValue("ci-fixture")
                .header("alg","ES256")
                .subject(id.toString())
                .audience(List.of("authenticated"))
                .claim("role","authenticated")
                .issuedAt(Instant.now().minusSeconds(15))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_AUTHENTICATED"))));
    }

    private static List<String> products() {
        return executor.read(j -> j.queryForList(
                "select nome from stockos_private.produtos order by nome",String.class));
    }

    @Test
    void deniesMissingTokenAndRunsUnderActualRestrictedNonOwnerRole() {
        SecurityContextHolder.clearContext();
        assertEquals(401, assertThrows(ResponseStatusException.class,
                SaasRlsReadExecutorPostgresIntegrationTest::products).getStatusCode().value());
        login(A);
        assertEquals("stockos_ci_reader",executor.read(j ->
                j.queryForObject("select current_user",String.class)));
        assertEquals(List.of("Produto A"),products());
    }

    @Test
    void neverLeaksAnActorAcrossConnectionsTransactionsOrCompanyBoundaries() {
        login(A);
        assertEquals(List.of("Produto A"),products());
        // A second independent read must establish its own local identity.
        login(B);
        assertEquals(List.of("Produto B"),products());
        login(DISABLED);
        assertTrue(products().isEmpty());
        login(UUID.randomUUID());
        assertTrue(products().isEmpty());
        login(A);
        assertEquals(List.of("Produto A"),products());
        assertEquals("",jdbc.queryForObject(
                "select coalesce(current_setting('stockos.actor_id',true),'')",String.class),
                "Actor GUC must not survive commit on the reusable connection");
    }

    @Test
    void rollbackRemovesContextAndNoOuterTransactionCanInheritIdentity() {
        login(A);
        assertThrows(IllegalArgumentException.class,()->executor.read(j -> {
            assertEquals(List.of("Produto A"),j.queryForList(
                    "select nome from stockos_private.produtos",String.class));
            throw new IllegalArgumentException("force rollback");
        }));
        assertEquals("",jdbc.queryForObject(
                "select coalesce(current_setting('stockos.actor_id',true),'')",String.class));
        login(B);
        assertEquals(List.of("Produto B"),products());

        var outer = new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class,()->outer.execute(status ->
                executor.read(j -> j.queryForObject(
                        "select count(*) from stockos_private.empresas",Integer.class))));
    }

    @Test
    void readOnlyRoleCannotWriteOrReadInvitationSecrets() {
        login(A);
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                executor.read(j -> j.update(
                        "update stockos_private.produtos set estoque_atual=100")));
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                executor.read(j -> j.queryForList(
                        "select * from stockos_private.convites_funcionarios")));
    }
}
