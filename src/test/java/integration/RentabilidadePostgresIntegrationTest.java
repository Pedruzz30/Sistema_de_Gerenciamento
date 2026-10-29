package integration;

import app.Main;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import repository.CotacaoRepository;
import repository.PedidoRepository;
import service.RentabilidadeService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL regression (GitHub Actions ephemeral database).
 * No writes are made to the developer's PostgreSQL or Supabase.
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.show-sql=false",
                "stockos.seed.demo-menu.enabled=false",
                "stockos.schema-compatibility-runner.enabled=false"
        })
@Transactional
class RentabilidadePostgresIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired CotacaoRepository cotacoes;
    @Autowired PedidoRepository pedidos;
    @Autowired RentabilidadeService rentabilidade;

    @Test
    void rankingAgregadoPreservaUltimaCotacaoEVendasSemContarAjustes() {
        Integer id = jdbc.queryForObject("""
            insert into produtos(nome, quantidade_atual, quantidade_minima, preco_unitario,
                                 controlado_por_estoque, ativo)
            values ('PERF PG demo', 50, 5, 30.00, true, true)
            returning id
            """, Integer.class);
        assertNotNull(id);

        jdbc.update("insert into cotacoes_mensais(produto_id, mes_referencia, preco_unitario, quantidade_comprada) values (?, '2026-01', 14.00, 12)", id);
        jdbc.update("insert into cotacoes_mensais(produto_id, mes_referencia, preco_unitario, quantidade_comprada) values (?, '2026-06', 12.00, 12)", id);

        jdbc.update("""
            insert into movimentacoes_estoque(produto_id, quantidade, tipo_movimentacao,
                data_hora, descricao, saldo_anterior, saldo_posterior)
            values (?, 2, 'SAIDA', current_timestamp, 'Saida via venda no caixa', 50, 48)
            """, id);
        jdbc.update("""
            insert into movimentacoes_estoque(produto_id, quantidade, tipo_movimentacao,
                data_hora, descricao, saldo_anterior, saldo_posterior)
            values (?, 3, 'SAIDA', current_timestamp, 'VENDA no balcão', 48, 45)
            """, id);
        jdbc.update("""
            insert into movimentacoes_estoque(produto_id, quantidade, tipo_movimentacao,
                data_hora, descricao, saldo_anterior, saldo_posterior)
            values (?, 4, 'SAIDA', current_timestamp, 'Ajuste por perda', 45, 41)
            """, id);
        jdbc.update("""
            insert into movimentacoes_estoque(produto_id, quantidade, tipo_movimentacao,
                data_hora, descricao, saldo_anterior, saldo_posterior)
            values (?, 7, 'ENTRADA', current_timestamp, 'Estorno venda', 41, 48)
            """, id);

        Map<Integer,Integer> agregado = pedidos.quantidadesVendidasPorProduto();
        Map<Integer,BigDecimal> custo = cotacoes.custosMaisRecentesPorProduto();

        assertEquals(5, agregado.get(id));
        assertEquals(0, new BigDecimal("12.00").compareTo(custo.get(id)));

        var item = rentabilidade.gerarRanking().itens().stream()
                .filter(i -> i.produtoId() == id).findFirst().orElseThrow();
        assertEquals(5, item.unidadesVendidas());
        assertEquals(0, new BigDecimal("18.00").compareTo(item.lucroUnitario()));
        assertEquals(0, new BigDecimal("90.00").compareTo(item.lucroTotalEstimado()));
        assertEquals(0, new BigDecimal("60.0000").compareTo(item.margemPercentual()));

        // Regressão: comparar agregação SQL com os dados legados do mesmo teste.
        int historico = pedidos.listarTodos().stream()
                .filter(p -> p.getProduto().getId() == id && p.ehSaida()
                        && p.getDescricao().toLowerCase(java.util.Locale.ROOT).contains("venda"))
                .mapToInt(model.Pedido::getQuantidade).sum();
        assertEquals(historico, agregado.get(id));
    }

    @Test
    void capturaPlanoDeExecucaoSomenteNoPostgresEfemero() {
        List<String> vendas = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
                SELECT produto_id, sum(quantidade)
                FROM movimentacoes_estoque
                WHERE tipo_movimentacao = 'SAIDA' AND lower(descricao) LIKE '%venda%'
                GROUP BY produto_id
                """, String.class);
        List<String> custos = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
                SELECT DISTINCT ON (produto_id) produto_id, preco_unitario
                FROM cotacoes_mensais
                ORDER BY produto_id, mes_referencia DESC, id DESC
                """, String.class);
        assertFalse(vendas.isEmpty());
        assertFalse(custos.isEmpty());
        System.out.println("EXPLAIN vendas (PostgreSQL temporário):\n" + String.join("\n", vendas));
        System.out.println("EXPLAIN custos (PostgreSQL temporário):\n" + String.join("\n", custos));
    }
}
