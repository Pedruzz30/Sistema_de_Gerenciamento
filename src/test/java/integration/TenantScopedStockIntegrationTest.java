package integration;

import app.Main;
import app.tenancy.TenantScopedStockService;
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
class TenantScopedStockIntegrationTest {
    private static final AtomicLong NEXT = new AtomicLong(87000000000L);
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantScopedStockService service;

    @Test
    void listaSomenteSaldoEMovimentoDaLojaAutorizada() {
        UUID a = loja("Estoque consulta A");
        UUID b = loja("Estoque consulta B");
        long ruA = usuario();
        long ruB = usuario();
        vincular(a, ruA);
        vincular(b, ruB);
        int produtoA = produto(a);
        int produtoB = produto(b);
        saldo(a, produtoA);
        saldo(b, produtoB);
        movimento(a, produtoA);
        movimento(b, produtoB);

        assertEquals(1, service.listarSaldos(a, ruA).size());
        assertEquals(produtoA, service.listarSaldos(a, ruA).get(0).produtoId());
        assertEquals(1, service.listarMovimentos(b, ruB).size());
        assertEquals(produtoB, service.listarMovimentos(b, ruB).get(0).produtoId());
        assertThrows(SecurityException.class, () -> service.listarSaldos(b, ruA));
        assertThrows(SecurityException.class, () -> service.listarMovimentos(a, ruB));
    }

    @Test
    void dadosLegadosSemTenantNaoAparecemNaConsultaDeLoja() {
        UUID a = loja("Loja sem estoque legado");
        long ru = usuario();
        vincular(a, ru);
        int legacy = produto(null);
        saldo(null, legacy);
        movimento(null, legacy);
        assertTrue(service.listarSaldos(a, ru).isEmpty());
        assertTrue(service.listarMovimentos(a, ru).isEmpty());
    }

    private UUID loja(String nome) {
        return jdbc.queryForObject(
                "insert into estabelecimentos (nome) values (?) returning id",
                UUID.class, nome);
    }

    private long usuario() {
        return jdbc.queryForObject("""
                insert into usuarios (nome, sobrenome, cpf, senha, ativo, perfil)
                values ('Teste', 'Estoque', ?, 'somente-teste', true, 'OPERADOR') returning ru
                """, Long.class, Long.toString(NEXT.incrementAndGet()));
    }

    private void vincular(UUID tenant, long ru) {
        jdbc.update("""
                insert into usuarios_estabelecimentos
                    (estabelecimento_id, usuario_ru, ru_local, papel)
                values (?, ?, 1, 'OPERADOR')
                """, tenant, ru);
    }

    private int produto(UUID tenant) {
        return jdbc.queryForObject("""
                insert into produtos
                    (nome, quantidade_atual, quantidade_minima, preco_unitario, estabelecimento_id)
                values ('Saldo da loja', 8, 1, 5.00, ?) returning id
                """, Integer.class, tenant);
    }

    private void saldo(UUID tenant, int produto) {
        jdbc.update("""
                insert into estoques_produto
                    (filial_id, produto_id, quantidade_atual, quantidade_reservada,
                     custo_medio, version, atualizado_em, estabelecimento_id)
                values (?, ?, 8, 0, 5.00, 0, now(), ?)
                """, NEXT.incrementAndGet(), produto, tenant);
    }

    private void movimento(UUID tenant, int produto) {
        jdbc.update("""
                insert into movimentos_estoque_decimal
                    (filial_id, produto_id, tipo, quantidade_base, saldo_anterior,
                     saldo_posterior, custo_unitario, custo_total, origem_tipo, origem_id,
                     motivo, criado_em, estabelecimento_id)
                values (?, ?, 'ENTRADA_COMPRA', 1, 7, 8, 5.00, 5.00,
                        'TESTE', 1, 'Teste de isolamento', now(), ?)
                """, NEXT.incrementAndGet(), produto, tenant);
    }
}
