package integration;

import app.Main;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Runs real Spring MVC + SecurityFilterChain + transactional services against
 * disposable PostgreSQL. JwtDecoder is MOCKED so no real Supabase is contacted.
 * This checks HTTP routing and server-side tenant authorization, NOT actual
 * JWT signature/JWKS validation.
 */
@EnabledIfEnvironmentVariable(named = "STOCKOS_TEST_DB_URL",
    matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/stockos_ci_test")
@SpringBootTest(classes=Main.class, properties={
    "spring.profiles.active=test",
    "stockos.security.mode=supabase-jwt",
    "stockos.security.supabase-issuer=https://stockos-ci.supabase.co/auth/v1",
    "stockos.supabase.url=https://stockos-ci.supabase.co",
    "stockos.supabase.publishable-key=sb_publishable_this_is_only_a_ci_fixture_key",
    "spring.flyway.baseline-on-migrate=false",
    "stockos.admin.enabled=false",
    "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class SaasHttpSecurityPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder jwtDecoder;

    private UUID company;
    private UUID filial;
    private UUID product;
    private UUID owner;
    private UUID clerk;
    private UUID stranger;

    @BeforeEach
    void prepare() {
        company=UUID.randomUUID();
        filial=UUID.randomUUID();
        product=UUID.randomUUID();
        owner=UUID.randomUUID();
        clerk=UUID.randomUUID();
        stranger=UUID.randomUUID();
        UUID plan=UUID.randomUUID();

        jdbc.update("insert into stockos_private.empresas(id,nome) values (?,?)",
                    company,"HTTP fixture "+company);
        jdbc.update("insert into stockos_private.filiais(id,empresa_id,nome) values (?,?,'Local')",
                    filial,company);
        jdbc.update("""
            insert into stockos_private.membros(empresa_id,auth_user_id,papel)
            values (?,?,'PROPRIETARIO')
            """,company,owner);
        jdbc.update("""
            insert into stockos_private.membros(empresa_id,auth_user_id,papel)
            values (?,?,'ATENDENTE')
            """,company,clerk);
        jdbc.update("""
            insert into stockos_private.planos
            (id,codigo,nome,preco_mensal,max_filiais,max_usuarios)
            values (?,?,?,?,1,10)
            """,plan,"h_"+company.toString().replace("-","").substring(0,29),"CI HTTP",BigDecimal.ZERO);
        jdbc.update("""
            insert into stockos_private.licencas
             (empresa_id,plano_id,status,vigencia_inicio,vigencia_fim,valor_mensal_snapshot)
            values (?,?,'TRIAL',now()-interval '1 day',now()+interval '1 day',0)
            """,company,plan);
        jdbc.update("""
            insert into stockos_private.produtos
             (id,empresa_id,filial_id,codigo,nome,preco_unitario,estoque_atual)
            values (?,?,?,'WATER','Agua',10.00,10)
            """,product,company,filial);
        stub("owner-test", owner);
        stub("clerk-test", clerk);
        stub("stranger-test", stranger);
        when(jwtDecoder.decode(eq("invalid-test")))
            .thenThrow(new BadJwtException("Invalid signature in test fixture"));
    }

    private void stub(String token, UUID userId) {
        Jwt jwt=Jwt.withTokenValue(token)
            .header("alg","RS256")
            .subject(userId.toString())
            .audience(List.of("authenticated"))
            .claim("role","authenticated")
            .issuedAt(Instant.now().minusSeconds(30))
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        when(jwtDecoder.decode(eq(token))).thenReturn(jwt);
    }
    private static String branchBase(UUID company,UUID branch) {
        return "/api/saas/empresas/"+company+"/filiais/"+branch;
    }

    @Test
    void noTokenOrForgeryCannotReachPrivateApis() throws Exception {
        String path=branchBase(company,filial)+"/comandas";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-User-RU","52998224725"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization","Bearer invalid-test"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/legacy/admin").header("Authorization","Bearer owner-test"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login")
                .header("Authorization","Bearer owner-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanSeeOnlyOwnCompanyAndBranch() throws Exception {
        mvc.perform(get("/api/saas/empresas").header("Authorization","Bearer owner-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='"+company+"')]").isNotEmpty());
        mvc.perform(get(branchBase(company,filial)+"/produtos")
                .header("Authorization","Bearer owner-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$",hasSize(1)));
        mvc.perform(get(branchBase(company,UUID.randomUUID())+"/produtos")
                .header("Authorization","Bearer owner-test"))
                .andExpect(status().isNotFound());
        mvc.perform(get(branchBase(company,filial)+"/produtos")
                .header("Authorization","Bearer stranger-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeesCannotEditCatalogOrCloseCash() throws Exception {
        mvc.perform(get(branchBase(company,filial)+"/produtos")
                .header("Authorization","Bearer clerk-test"))
                .andExpect(status().isOk());
        mvc.perform(post(branchBase(company,filial)+"/produtos")
                .header("Authorization","Bearer clerk-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"codigo":"ITEM","nome":"Bebida","precoUnitario":10,"estoqueInicial":2}
                    """)).andExpect(status().isForbidden());
        mvc.perform(post(branchBase(company,filial)+"/caixa/fechar")
                .header("Authorization","Bearer clerk-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorContado\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendedLicenseBlocksOperationsButAllowsReadingStatus() throws Exception {
        jdbc.update("update stockos_private.licencas set status='SUSPENSA' where empresa_id=?",company);
        mvc.perform(get("/api/saas/empresas/"+company+"/licenca")
                .header("Authorization","Bearer owner-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acesso_liberado").value(false));
        mvc.perform(get(branchBase(company,filial)+"/comandas")
                .header("Authorization","Bearer owner-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanOpenTabViaHttpAndResponseStaysTenantScoped() throws Exception {
        mvc.perform(post(branchBase(company,filial)+"/comandas")
                .header("Authorization","Bearer owner-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(2));
        mvc.perform(get(branchBase(company,filial)+"/comandas")
                .header("Authorization","Bearer owner-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$",hasSize(1)));
        mvc.perform(get(branchBase(company,filial)+"/comandas")
                .header("Authorization","Bearer stranger-test"))
                .andExpect(status().isForbidden());
    }
}
