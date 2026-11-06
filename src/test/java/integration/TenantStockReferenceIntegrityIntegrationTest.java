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
class TenantStockReferenceIntegrityIntegrationTest {
    private static final AtomicLong FILIAL = new AtomicLong(400000L);
    @Autowired JdbcTemplate jdbc;

    @Test
    void permiteSaldoEEstornoNaPropriaLoja() {
        UUID loja = novaLoja("Estoque autorizado");
        int produto = produto(loja);
        saldo(loja, produto);
        long original = movimento(loja, produto, null);
        long estorno = movimento(loja, produto, original);
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from movimentos_estoque_decimal
                where estabelecimento_id = ? and id in (?, ?)
                """, Integer.class, loja, original, estorno));
    }

    @Test
    void bloqueiaSaldoComProdutoDeOutraLoja() {
        UUID lojaA = novaLoja("Saldo loja A");
        UUID lojaB = novaLoja("Produto loja B");
        int produtoB = produto(lojaB);
        assertThrows(DataIntegrityViolationException.class,
                () -> saldo(lojaA, produtoB));
    }

    @Test
    void bloqueiaMovimentoSemTenantMesmoComProdutoDeLoja() {
        UUID loja = novaLoja("Movimento protegido");
        int produto = produto(loja);
        assertThrows(DataIntegrityViolationException.class,
                () -> movimento(null, produto, null));
    }

    @Test
    void bloqueiaEstornoDeMovimentoDeOutraLoja() {
        UUID lojaA = novaLoja("Movimento A");
        UUID lojaB = novaLoja("Movimento B");
        int produtoA = produto(lojaA);
        int produtoB = produto(lojaB);
        long movimentoB = movimento(lojaB, produtoB, null);
        assertThrows(DataIntegrityViolationException.class,
                () -> movimento(lojaA, produtoA, movimentoB));
    }

    @Test
    void bloqueiaMudancaDeProprietarioDoSaldo() {
        UUID lojaA = novaLoja("Estoque original");
        UUID lojaB = novaLoja("Estoque destino");
        int produto = produto(lojaA);
        long id = saldo(lojaA, produto);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("update estoques_produto set estabelecimento_id = ? where id = ?",
                        lojaB, id));
    }

    @Test
    void preservaEstoqueLegadoSemDono() {
        int produto = produto(null);
        long id = saldo(null, produto);
        assertNull(jdbc.queryForObject(
                "select estabelecimento_id from estoques_produto where id = ?",
                UUID.class, id));
    }

    private UUID novaLoja(String nome) {
        return jdbc.queryForObject(
                "insert into estabelecimentos (nome) values (?) returning id",
                UUID.class, nome);
    }

    private int produto(UUID tenant) {
        return jdbc.queryForObject("""
                insert into produtos
                    (nome, quantidade_atual, quantidade_minima, preco_unitario, estabelecimento_id)
                values ('Produto Estoque Teste', 10, 1, 5.00, ?) returning id
                """, Integer.class, tenant);
    }

    private long saldo(UUID tenant, int produto) {
        return jdbc.queryForObject("""
                insert into estoques_produto
                    (filial_id, produto_id, quantidade_atual, quantidade_reservada,
                     custo_medio, version, atualizado_em, estabelecimento_id)
                values (?, ?, 10, 0, 5.00, 0, now(), ?) returning id
                """, Long.class, FILIAL.incrementAndGet(), produto, tenant);
    }

    private long movimento(UUID tenant, int produto, Long estornado) {
        return jdbc.queryForObject("""
                insert into movimentos_estoque_decimal
                    (filial_id, produto_id, tipo, quantidade_base, saldo_anterior,
                     saldo_posterior, custo_unitario, custo_total, origem_tipo, origem_id,
                     motivo, criado_em, movimento_estornado_id, estabelecimento_id)
                values (?, ?, 'ENTRADA_COMPRA', 1, 9, 10, 5.00, 5.00,
                        'TESTE', 1, 'Teste de isolamento', now(), ?, ?)
                returning id
                """, Long.class, FILIAL.incrementAndGet(), produto, estornado, tenant);
    }
}
