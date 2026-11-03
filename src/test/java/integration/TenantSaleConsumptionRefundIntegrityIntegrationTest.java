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
class TenantSaleConsumptionRefundIntegrityIntegrationTest {
    private static final AtomicLong NEXT = new AtomicLong(900000L);
    @Autowired JdbcTemplate jdbc;

    @Test
    void permiteConsumoEEstornoExatoDaMesmaLoja() {
        Fixture a = fixture();
        long consumo = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long estornoMovimento = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        long refundId = estorno(a.tenant, consumo, estornoMovimento);
        assertEquals(a.tenant, jdbc.queryForObject(
                "select estabelecimento_id from venda_item_consumo_estornos where id = ?",
                UUID.class, refundId));
    }

    @Test
    void rejeitaConsumoComProdutoDaOutraLoja() {
        Fixture a = fixture();
        Fixture b = fixture();
        assertThrows(DataIntegrityViolationException.class,
                () -> consumo(a.tenant, a.saleItem, b.product, a.unit, null));
    }

    @Test
    void rejeitaMovimentoDeConsumoDeOutraLojaOuOutroProduto() {
        Fixture a = fixture();
        Fixture b = fixture();
        assertThrows(DataIntegrityViolationException.class,
                () -> consumo(a.tenant, a.saleItem, a.product, a.unit, b.originalMove));
    }

    @Test
    void rejeitaOmissaoDoTenantNoConsumo() {
        Fixture a = fixture();
        assertThrows(DataIntegrityViolationException.class,
                () -> consumo(null, a.saleItem, a.product, a.unit, a.originalMove));
    }

    @Test
    void rejeitaEstornoQueReferenciaConsumoDaOutraLoja() {
        Fixture a = fixture();
        Fixture b = fixture();
        long consumoA = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long estornoB = movimento(b.tenant, b.product, "ESTORNO_VENDA", b.originalMove);
        assertThrows(DataIntegrityViolationException.class,
                () -> estorno(b.tenant, consumoA, estornoB));
    }

    @Test
    void rejeitaEstornoQueNaoReverteMovimentoOriginal() {
        Fixture a = fixture();
        long consumoA = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long other = movimento(a.tenant, a.product, "SAIDA_VENDA", null);
        long wrongRefund = movimento(a.tenant, a.product, "ESTORNO_VENDA", other);
        assertThrows(DataIntegrityViolationException.class,
                () -> estorno(a.tenant, consumoA, wrongRefund));
    }

    @Test
    void rejeitaEstornoSemTenantMesmoQueConsumoTenhaDono() {
        Fixture a = fixture();
        long consumoA = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long refundMove = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        assertThrows(DataIntegrityViolationException.class,
                () -> estorno(null, consumoA, refundMove));
    }

    @Test
    void aceitaEstornoParcialDentroDoConsumoEMovimento() {
        Fixture a = fixture();
        long consumption = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long refundMovement = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        long first = estorno(a.tenant, consumption, refundMovement, "0.400000");
        long second = estorno(a.tenant, consumption, refundMovement, "0.600000");
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from venda_item_consumo_estornos
                where estabelecimento_id = ? and id in (?, ?)
                """, Integer.class, a.tenant, first, second));
    }

    @Test
    void rejeitaSomaDeEstornosMaiorQueConsumo() {
        Fixture a = fixture();
        long consumption = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long firstMovement = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        long secondMovement = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        estorno(a.tenant, consumption, firstMovement, "0.700000");
        assertThrows(DataIntegrityViolationException.class,
                () -> estorno(a.tenant, consumption, secondMovement, "0.400000"));
    }

    @Test
    void rejeitaDistribuicaoMaiorQueQuantidadeDoMovimentoDeEstorno() {
        Fixture a = fixture();
        long consumptionOne = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long consumptionTwo = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long refundMovement = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        estorno(a.tenant, consumptionOne, refundMovement, "0.700000");
        assertThrows(DataIntegrityViolationException.class,
                () -> estorno(a.tenant, consumptionTwo, refundMovement, "0.400000"));
    }

    @Test
    void rejeitaReducaoDaQuantidadeConsumidaAposEstorno() {
        Fixture a = fixture();
        long consumption = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long refundMovement = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        estorno(a.tenant, consumption, refundMovement, "0.500000");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "update venda_item_consumos set quantidade_base_total = 0.3 where id = ?",
                        consumption));
    }

    @Test
    void rejeitaReducaoDoMovimentoJaVinculadoAEstorno() {
        Fixture a = fixture();
        long consumption = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long refundMovement = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        estorno(a.tenant, consumption, refundMovement, "0.700000");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "update movimentos_estoque_decimal set quantidade_base = 0.4 where id = ?",
                        refundMovement));
    }

    @Test
    void bloqueiaConsumoSemMovimentoEmLojaTenant() {
        Fixture a = fixture();
        assertThrows(DataIntegrityViolationException.class,
                () -> consumo(a.tenant, a.saleItem, a.product, a.unit, null));
    }

    @Test
    void bloqueiaDuplaAlocacaoDaSaidaDeEstoque() {
        Fixture a = fixture();
        consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        assertThrows(DataIntegrityViolationException.class,
                () -> consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove));
    }

    @Test
    void bloqueiaAumentoDeConsumoAcimaDaSaida() {
        Fixture a = fixture();
        long consumoId = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "update venda_item_consumos set quantidade_base_total = 1.2 where id = ?",
                        consumoId));
    }

    @Test
    void bloqueiaReescritaDeMovimentoJaConsumido() {
        Fixture a = fixture();
        consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "update movimentos_estoque_decimal set tipo = 'AJUSTE_NEGATIVO' where id = ?",
                        a.originalMove));
    }

    @Test
    void bloqueiaReescritaDeEstornoJaVinculado() {
        Fixture a = fixture();
        long consumption = consumo(a.tenant, a.saleItem, a.product, a.unit, a.originalMove);
        long refund = movimento(a.tenant, a.product, "ESTORNO_VENDA", a.originalMove);
        estorno(a.tenant, consumption, refund);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "update movimentos_estoque_decimal set movimento_estornado_id = null where id = ?",
                        refund));
    }

    private Fixture fixture() {
        UUID tenant = jdbc.queryForObject(
                "insert into estabelecimentos(nome) values ('Teste consumo') returning id", UUID.class);
        long n = NEXT.incrementAndGet();
        long unit = jdbc.queryForObject("""
                insert into unidades_medida(codigo, nome, dimensao, fator_base, ativa)
                values (?, 'Unidade teste', 'CONTAGEM', 1, true) returning id
                """, Long.class, "TESTU_" + n);
        int product = jdbc.queryForObject("""
                insert into produtos(nome, quantidade_atual, quantidade_minima,
                                     preco_unitario, estabelecimento_id, unidade_base_id)
                values ('Produto consumo', 10, 1, 5, ?, ?) returning id
                """, Integer.class, tenant, unit);
        int category = jdbc.queryForObject("""
                insert into categorias_cardapio(codigo, nome_exibicao, ordem_exibicao, ativo, estabelecimento_id)
                values (?, 'Categoria consumo', 1, true, ?) returning id
                """, Integer.class, "CONS_CAT_" + n, tenant);
        int item = jdbc.queryForObject("""
                insert into itens_cardapio(codigo, nome, preco_venda, categoria_cardapio_id, tipo_item,
                    ativo, disponivel, ordem_exibicao, estabelecimento_id)
                values (?, 'Item consumo', 10, ?, 'PREPARADO_SOB_DEMANDA', true, true, 1, ?)
                returning id
                """, Integer.class, "CONS_ITEM_" + n, category, tenant);
        long variation = jdbc.queryForObject("""
                insert into variacoes_item_cardapio(item_cardapio_id, nome, codigo,
                    preco_venda, tipo_composicao, ativa, padrao, ordem_exibicao, criado_em, atualizado_em)
                values (?, 'Variacao', ?, 10, 'PREPARADO', true, false, 1, now(), now())
                returning id
                """, Long.class, item, "CONS_VAR_" + n);
        long sale = jdbc.queryForObject("""
                insert into vendas(filial_id, numero, status, canal, subtotal, desconto_total,
                    acrescimo_total, valor_total, custo_ingredientes_total,
                    margem_bruta_estimada_total, criada_em, estabelecimento_id)
                values (?, ?, 'RASCUNHO', 'SALAO', 0, 0, 0, 0, 0, 0, now(), ?) returning id
                """, Long.class, n, "CONS_SALE_" + n, tenant);
        long saleItem = jdbc.queryForObject("""
                insert into venda_itens(venda_id, item_cardapio_id, variacao_item_cardapio_id,
                    quantidade, nome_item_snapshot, nome_variacao_snapshot,
                    codigo_variacao_snapshot, tipo_composicao_snapshot,
                    preco_unitario_base, desconto_unitario, acrescimo_unitario,
                    preco_unitario_final, subtotal, custo_unitario_estimado,
                    custo_total_estimado, margem_bruta_unitaria_estimada,
                    margem_bruta_total_estimada, status, criado_em, estabelecimento_id)
                values (?, ?, ?, 1, 'Item consumo', 'Variacao', 'TESTE', 'PREPARADO',
                    10, 0, 0, 10, 10, 2, 2, 8, 8, 'ATIVO', now(), ?) returning id
                """, Long.class, sale, item, variation, tenant);
        long originalMove = movimento(tenant, product, "SAIDA_VENDA", null);
        return new Fixture(tenant, unit, product, saleItem, originalMove);
    }

    private long movimento(UUID tenant, int product, String tipo, Long reversed) {
        long n = NEXT.incrementAndGet();
        return jdbc.queryForObject("""
                insert into movimentos_estoque_decimal(
                    filial_id, produto_id, tipo, quantidade_base, saldo_anterior, saldo_posterior,
                    custo_unitario, custo_total, origem_tipo, origem_id,
                    motivo, criado_em, movimento_estornado_id, estabelecimento_id)
                values (?, ?, ?, 1, 10, 9, 2, 2, 'TESTE', ?, 'Teste', now(), ?, ?)
                returning id
                """, Long.class, n, product, tipo, n, reversed, tenant);
    }

    private long consumo(UUID tenant, long saleItem, int product, long unit, Long movement) {
        return jdbc.queryForObject("""
                insert into venda_item_consumos(
                    venda_item_id, produto_id, nome_produto_snapshot, unidade_base_id,
                    unidade_base_codigo_snapshot, quantidade_base_por_unidade,
                    quantidade_base_total, custo_medio_unitario_snapshot,
                    custo_total_snapshot, origem_composicao, movimento_estoque_id,
                    criado_em, estabelecimento_id)
                values (?, ?, 'Produto consumo', ?, 'UN', 1, 1, 2, 2,
                        'PRODUTO_DIRETO', ?, now(), ?) returning id
                """, Long.class, saleItem, product, unit, movement, tenant);
    }

    private long estorno(UUID tenant, long consumption, long movement) {
        return estorno(tenant, consumption, movement, "1.000000");
    }

    private long estorno(UUID tenant, long consumption, long movement, String quantity) {
        return jdbc.queryForObject("""
                insert into venda_item_consumo_estornos(
                    venda_item_consumo_id, movimento_estoque_id,
                    quantidade_base_estornada, custo_unitario_snapshot,
                    valor_estornado, criado_em, estabelecimento_id)
                values (?, ?, ?, 2, 2, now(), ?) returning id
                """, Long.class, consumption, movement, new java.math.BigDecimal(quantity), tenant);
    }

    private record Fixture(UUID tenant, long unit, int product, long saleItem, long originalMove) {}
}
