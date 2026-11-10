package integration;

import app.Main;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TenantCashReferenceIntegrityIntegrationTest {
    private static final AtomicLong NEXT = new AtomicLong(89000000000L);
    @Autowired JdbcTemplate jdbc;

    @Test
    void permiteMovimentacaoDaPropriaLojaComOperadorAtivo() {
        UUID tenant = loja("Caixa regular");
        long operator = usuario();
        vincular(tenant, operator);
        long register = caixa(tenant, operator);
        long movement = movimento(tenant, register, operator);
        assertEquals(tenant, jdbc.queryForObject(
                "select estabelecimento_id from movimentacoes_caixa where id = ?",
                UUID.class, movement));
    }

    @Test
    void impedeMovimentoDaLojaAUsandoCaixaDaLojaB() {
        UUID a = loja("Movimento A");
        UUID b = loja("Caixa B");
        long operator = usuario();
        vincular(a, operator);
        long registerB = caixa(b, null);
        assertThrows(DataIntegrityViolationException.class,
                () -> movimento(a, registerB, operator));
    }

    @Test
    void impedeMovimentoComOperadorSemVinculo() {
        UUID tenant = loja("Caixa sem permissao");
        long linked = usuario();
        long unlinked = usuario();
        vincular(tenant, linked);
        long register = caixa(tenant, linked);
        assertThrows(DataIntegrityViolationException.class,
                () -> movimento(tenant, register, unlinked));
    }

    @Test
    void impedeCaixaComOperadorSemVinculo() {
        UUID tenant = loja("Caixa de outra pessoa");
        long unlinked = usuario();
        assertThrows(DataIntegrityViolationException.class,
                () -> caixa(tenant, unlinked));
    }

    @Test
    void impedeOmissaoTenantEmMovimentoNovo() {
        UUID tenant = loja("Caixa com tenant obrigatorio");
        long operator = usuario();
        vincular(tenant, operator);
        long register = caixa(tenant, operator);
        assertThrows(DataIntegrityViolationException.class,
                () -> movimento(null, register, operator));
    }

    @Test
    void preservaMovimentoDeCaixaLegadoSemTenant() {
        long operator = usuario();
        long register = caixa(null, operator);
        long movement = movimento(null, register, operator);
        assertNull(jdbc.queryForObject(
                "select estabelecimento_id from movimentacoes_caixa where id = ?",
                UUID.class, movement));
    }

    private UUID loja(String name) {
        return jdbc.queryForObject(
                "insert into estabelecimentos (nome) values (?) returning id", UUID.class, name);
    }

    private long usuario() {
        return jdbc.queryForObject("""
                insert into usuarios(nome, sobrenome, cpf, senha, ativo, perfil)
                values ('Teste', 'Caixa', ?, 'somente-teste', true, 'OPERADOR')
                returning ru
                """, Long.class, Long.toString(NEXT.incrementAndGet()));
    }

    private void vincular(UUID tenant, long ru) {
        jdbc.update("""
                insert into usuarios_estabelecimentos
                    (estabelecimento_id, usuario_ru, ru_local, papel)
                values (?, ?, 1, 'OPERADOR')
                """, tenant, ru);
    }

    private long caixa(UUID tenant, Long operator) {
        return jdbc.queryForObject("""
                insert into caixas
                    (numero_caixa, saldo_inicial, saldo_atual, status,
                     operador_atual_ru, estabelecimento_id)
                values (?, 0, 0, 'FECHADO', ?, ?) returning id
                """, Long.class, Math.toIntExact(NEXT.incrementAndGet() % 100000000), operator, tenant);
    }

    private long movimento(UUID tenant, long caixa, long operator) {
        long id = NEXT.incrementAndGet();
        jdbc.update("""
                insert into movimentacoes_caixa
                    (id, tipo, valor, descricao, data_hora,
                     operador_ru, caixa_id, estabelecimento_id)
                values (?, 'SUPRIMENTO', 10.00, 'Teste de escopo', now(), ?, ?, ?)
                """, id, operator, caixa, tenant);
        return id;
    }
}
