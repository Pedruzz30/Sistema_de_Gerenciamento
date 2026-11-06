package integration;

import app.Main;
import app.tenancy.TenantScopedCashService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TenantScopedCashIntegrationTest {
    private static final AtomicLong NEXT = new AtomicLong(88000000000L);
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantScopedCashService service;

    @Test
    void gerenteConsultaSomenteSeusCaixasEMovimentos() {
        UUID a = loja("Relatorio caixa A");
        UUID b = loja("Relatorio caixa B");
        long userA = usuario();
        long userB = usuario();
        vincular(a, userA, "GERENTE");
        vincular(b, userB, "GERENTE");
        long caixaA = caixa(a, userA);
        long caixaB = caixa(b, userB);
        movimento(a, userA, caixaA);
        movimento(b, userB, caixaB);

        assertEquals(1, service.listarCaixas(a, userA).size());
        assertEquals(caixaA, service.listarCaixas(a, userA).get(0).id());
        assertEquals(1, service.listarMovimentos(b, userB).size());
        assertEquals(caixaB, service.listarMovimentos(b, userB).get(0).caixaId());
        assertThrows(SecurityException.class, () -> service.listarCaixas(b, userA));
        assertThrows(SecurityException.class, () -> service.listarMovimentos(a, userB));
    }

    @Test
    void operadorNaoPodeLerCaixasFinanceiros() {
        UUID tenant = loja("Financeiro restrito");
        long ru = usuario();
        vincular(tenant, ru, "OPERADOR");
        caixa(tenant, ru);
        assertThrows(SecurityException.class, () -> service.listarCaixas(tenant, ru));
        assertThrows(SecurityException.class, () -> service.listarMovimentos(tenant, ru));
    }

    @Test
    void caixaLegadoNaoApareceNoEscopoDeOutraLoja() {
        UUID tenant = loja("Financeiro novo");
        long ru = usuario();
        vincular(tenant, ru, "PROPRIETARIO");
        caixa(null, ru);
        assertTrue(service.listarCaixas(tenant, ru).isEmpty());
    }

    private UUID loja(String name) {
        return jdbc.queryForObject(
                "insert into estabelecimentos(nome) values (?) returning id", UUID.class, name);
    }

    private long usuario() {
        return jdbc.queryForObject("""
                insert into usuarios(nome, sobrenome, cpf, senha, ativo, perfil)
                values ('Teste', 'Caixa', ?, 'somente-teste', true, 'OPERADOR')
                returning ru
                """, Long.class, Long.toString(NEXT.incrementAndGet()));
    }

    private void vincular(UUID tenant, long user, String role) {
        jdbc.update("""
                insert into usuarios_estabelecimentos
                    (estabelecimento_id, usuario_ru, ru_local, papel)
                values (?, ?, 1, ?)
                """, tenant, user, role);
    }

    private long caixa(UUID tenant, long operator) {
        return jdbc.queryForObject("""
                insert into caixas(numero_caixa, saldo_inicial, saldo_atual, status,
                                   operador_atual_ru, estabelecimento_id)
                values (?, 0, 0, 'FECHADO', ?, ?) returning id
                """, Long.class, Math.toIntExact(NEXT.incrementAndGet() % 100000000), operator, tenant);
    }

    private void movimento(UUID tenant, long operator, long register) {
        jdbc.update("""
                insert into movimentacoes_caixa
                    (id, tipo, valor, descricao, data_hora, operador_ru, caixa_id,
                     estabelecimento_id)
                values (?, 'ENTRADA', 10.00, 'Movimento de teste', now(), ?, ?, ?)
                """, NEXT.incrementAndGet(), operator, register, tenant);
    }
}
