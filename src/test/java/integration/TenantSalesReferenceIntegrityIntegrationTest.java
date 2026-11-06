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
class TenantSalesReferenceIntegrityIntegrationTest {
    private static final AtomicLong NEXT = new AtomicLong(410000L);
    @Autowired JdbcTemplate jdbc;

    @Test
    void permiteVendaComItemEVariacaoDaMesmaLoja() {
        UUID tenant = loja("Venda Permitida");
        int category = categoria(tenant);
        int item = item(tenant, category);
        long variation = variacao(item);
        long sale = venda(tenant);
        long line = linha(tenant, sale, item, variation);

        assertEquals(tenant, jdbc.queryForObject(
                "select estabelecimento_id from venda_itens where id = ?", UUID.class, line));
    }

    @Test
    void bloqueiaItemDeCardapioDeOutraLoja() {
        UUID a = loja("Venda da Loja A");
        UUID b = loja("Cardapio da Loja B");
        int itemB = item(b, categoria(b));
        long variationB = variacao(itemB);
        long saleA = venda(a);

        assertThrows(DataIntegrityViolationException.class,
                () -> linha(a, saleA, itemB, variationB));
    }

    @Test
    void bloqueiaLinhaLigadaAVendaDeOutraLoja() {
        UUID a = loja("Item Loja A");
        UUID b = loja("Venda Loja B");
        int itemA = item(a, categoria(a));
        long variationA = variacao(itemA);
        long saleB = venda(b);

        assertThrows(DataIntegrityViolationException.class,
                () -> linha(a, saleB, itemA, variationA));
    }

    @Test
    void bloqueiaVariacaoQueNaoPertenceAoItem() {
        UUID tenant = loja("Variacao Fora do Item");
        int category = categoria(tenant);
        int itemA = item(tenant, category);
        int itemB = item(tenant, category);
        long variationB = variacao(itemB);
        long sale = venda(tenant);

        assertThrows(DataIntegrityViolationException.class,
                () -> linha(tenant, sale, itemA, variationB));
    }

    @Test
    void bloqueiaOmissaoDoTenantNaLinhaDeVenda() {
        UUID tenant = loja("Venda Sem Tenant");
        int item = item(tenant, categoria(tenant));
        long variation = variacao(item);
        long sale = venda(tenant);

        assertThrows(DataIntegrityViolationException.class,
                () -> linha(null, sale, item, variation));
    }

    @Test
    void preservaVendasLegadasSemTenant() {
        int item = item(null, categoria(null));
        long variation = variacao(item);
        long sale = venda(null);
        long line = linha(null, sale, item, variation);
        assertNull(jdbc.queryForObject(
                "select estabelecimento_id from venda_itens where id = ?", UUID.class, line));
    }

    private UUID loja(String name) {
        return jdbc.queryForObject(
                "insert into estabelecimentos(nome) values (?) returning id", UUID.class, name);
    }

    private int categoria(UUID tenant) {
        return jdbc.queryForObject("""
                insert into categorias_cardapio
                    (codigo, nome_exibicao, ordem_exibicao, ativo, estabelecimento_id)
                values (?, 'Categoria teste', 1, true, ?) returning id
                """, Integer.class, "V_CAT_" + NEXT.incrementAndGet(), tenant);
    }

    private int item(UUID tenant, int category) {
        return jdbc.queryForObject("""
                insert into itens_cardapio
                    (codigo, nome, preco_venda, categoria_cardapio_id, tipo_item,
                     ativo, disponivel, ordem_exibicao, estabelecimento_id)
                values (?, 'Item teste', 5.00, ?, 'PREPARADO_SOB_DEMANDA',
                        true, true, 1, ?) returning id
                """, Integer.class, "V_ITEM_" + NEXT.incrementAndGet(), category, tenant);
    }

    private long variacao(int item) {
        return jdbc.queryForObject("""
                insert into variacoes_item_cardapio
                    (item_cardapio_id, nome, codigo, preco_venda,
                     tipo_composicao, ativa, padrao, ordem_exibicao, criado_em, atualizado_em)
                values (?, 'Teste', ?, 5.00, 'PREPARADO',
                        true, false, 1, now(), now()) returning id
                """, Long.class, item, "V_VAR_" + NEXT.incrementAndGet());
    }

    private long venda(UUID tenant) {
        return jdbc.queryForObject("""
                insert into vendas
                    (filial_id, numero, status, canal, subtotal, desconto_total,
                     acrescimo_total, valor_total, custo_ingredientes_total,
                     margem_bruta_estimada_total, criada_em, estabelecimento_id)
                values (?, ?, 'RASCUNHO', 'SALAO', 0, 0, 0, 0, 0, 0, now(), ?)
                returning id
                """, Long.class, NEXT.incrementAndGet(), "V_SALE_" + NEXT.incrementAndGet(), tenant);
    }

    private long linha(UUID tenant, long venda, int item, long variation) {
        return jdbc.queryForObject("""
                insert into venda_itens
                    (venda_id, item_cardapio_id, variacao_item_cardapio_id,
                     quantidade, nome_item_snapshot, nome_variacao_snapshot,
                     codigo_variacao_snapshot, tipo_composicao_snapshot,
                     preco_unitario_base, desconto_unitario, acrescimo_unitario,
                     preco_unitario_final, subtotal, custo_unitario_estimado,
                     custo_total_estimado, margem_bruta_unitaria_estimada,
                     margem_bruta_total_estimada, status, criado_em, estabelecimento_id)
                values (?, ?, ?, 1, 'Item teste', 'Variacao teste', 'TESTE', 'PREPARADO',
                        5.00, 0, 0, 5.00, 5.00, 2.00, 2.00, 3.00, 3.00,
                        'ATIVO', now(), ?) returning id
                """, Long.class, venda, item, variation, tenant);
    }
}
