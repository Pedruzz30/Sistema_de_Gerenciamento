package integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import saas.StockOsStagingApplication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SaaS-only executable context against disposable PostgreSQL where integration
 * migrations have already created both legacy and SaaS tables.
 * The test explicitly ensures legacy Spring beans are NOT registered.
 */
@EnabledIfEnvironmentVariable(named="STOCKOS_TEST_DB_URL",
  matches="jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/stockos_ci_test")
@SpringBootTest(classes=StockOsStagingApplication.class,properties={
   "spring.profiles.active=staging",
   "stockos.security.mode=supabase-jwt",
   "stockos.security.supabase-issuer=https://stockos-ci.supabase.co/auth/v1",
   "stockos.supabase.url=https://stockos-ci.supabase.co",
   "stockos.supabase.expected-project-ref=stockos-ci",
   "stockos.supabase.publishable-key=sb_publishable_this_is_only_a_ci_fixture_key",
   "stockos.admin.enabled=false"
})
@AutoConfigureMockMvc
class SaasIsolatedStagingHttpIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void contextContainsOnlySaasBeansAndNoJpaOrFlywayOrLegacyRoutes() throws Exception {
        assertNotNull(context.getBean(controller.SaasComandaController.class));
        assertNotNull(context.getBean(service.SaasTenantAccess.class));
        assertEquals(0,context.getBeanNamesForType(repository.UsuarioRepository.class).length);
        assertEquals(0,context.getBeanNamesForType(controller.ProdutoController.class).length);
        assertEquals(0,context.getBeanNamesForType(app.AppConfig.class).length);
        assertTrue(context.getBeanNamesForType(org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean.class).length==0);
        assertEquals(0,context.getBeanNamesForType(org.flywaydb.core.Flyway.class).length);

        mvc.perform(get("/api/saas/empresas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/saas/config"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.supabaseUrl").value("https://stockos-ci.supabase.co"));
        // Authenticate the test bearer before checking legacy API denial.
        Jwt jwt=Jwt.withTokenValue("staging-test").header("alg","ES256")
            .subject(UUID.randomUUID().toString()).claim("role","authenticated")
            .audience(List.of("authenticated"))
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode(eq("staging-test"))).thenReturn(jwt);
        mvc.perform(get("/api/legacy/not-registered")
                .header("Authorization","Bearer staging-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtOwnerCanCallSaasApisWithoutLegacyDatabaseRepositories() throws Exception {
        UUID owner=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),plan=UUID.randomUUID();
        jdbc.update("insert into stockos_private.empresas(id,nome) values (?,?)",
                    company,"Isolated SaaS");
        jdbc.update("insert into stockos_private.filiais(id,empresa_id,nome) values (?,?,'Main')",
                    branch,company);
        jdbc.update("insert into stockos_private.membros(empresa_id,auth_user_id,papel) values (?,?,'PROPRIETARIO')",
                    company,owner);
        jdbc.update("""
          insert into stockos_private.planos(id,codigo,nome,preco_mensal,max_filiais,max_usuarios)
          values (?,?,?,0,1,10)
          """,plan,"s_"+company.toString().replace("-","").substring(0,29),"Staging");
        jdbc.update("""
          insert into stockos_private.licencas(empresa_id,plano_id,status,vigencia_inicio,
                 vigencia_fim,valor_mensal_snapshot)
          values (?,?,'TRIAL',now()-interval '1 hour',now()+interval '1 day',0)
          """,company,plan);
        Jwt jwt=Jwt.withTokenValue("staging-test").header("alg","ES256")
            .subject(owner.toString()).claim("role","authenticated")
            .audience(List.of("authenticated"))
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode(eq("staging-test"))).thenReturn(jwt);

        mvc.perform(get("/api/saas/empresas")
                .header("Authorization","Bearer staging-test"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.id=='"+company+"')]").isNotEmpty());
        mvc.perform(post("/api/saas/empresas/"+company+"/filiais/"+branch+"/comandas")
                .header("Authorization","Bearer staging-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":7}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.numero").value(7));
    }
}
