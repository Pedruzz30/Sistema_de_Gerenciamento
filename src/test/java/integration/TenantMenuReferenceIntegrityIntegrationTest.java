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
class TenantMenuReferenceIntegrityIntegrationTest {
    private static final AtomicLong NEXT_CODE = new AtomicLong(50000000L);
    @Autowired JdbcTemplate jdbc;

    @Test
    void mesmoEstabelecimentoPermiteItemDeCategoriaComProduto() {
        UUID tenant = loja("Menu Test A");
        int category = categoria(tenant);
        int product = produto(tenant);
        long item = item(tenant, category, product);
        assertEquals(tenant, jdbc.queryForObject(
                "select estabelecimento_id from itens_cardapio where id = ?",
                UUID.class, item));
    }

    @Test
    void naoPermiteCategoriaDeOutroEstabelecimento() {
        UUID tenantA = loja("Menu Test B");
        UUID tenantB = loja("Menu Test C");
        int categoryB = categoria(tenantB);
        assertThrows(DataIntegrityViolationException.class,
                () -> item(tenantA, categoryB, null));
    }

    @Test
    void naoPermiteProdutoDeOutroEstabelecimento() {
        UUID tenantA = loja("Menu Test D");
        UUID tenantB = loja("Menu Test E");
        int categoryA = categoria(tenantA);
        int productB = produto(tenantB);
        assertThrows(DataIntegrityViolationException.class,
                () -> item(tenantA, categoryA, productB));
    }

    @Test
    void naoPermiteOmitirTenantComCategoriaVinculada() {
        UUID tenant = loja("Menu Test F");
        int category = categoria(tenant);
        assertThrows(DataIntegrityViolationException.class,
                () -> item(null, category, null));
    }

    @Test
    void legadoSemTenantPermaneceCompatível() {
        int category = categoria(null);
        long item = item(null, category, null);
        assertNull(jdbc.queryForObject(
                "select estabelecimento_id from itens_cardapio where id = ?",
                UUID.class, item));
    }

    private UUID loja(String name) {
        return jdbc.queryForObject("insert into estabelecimentos (nome) values (?) returning id",
                UUID.class, name);
    }

    private int categoria(UUID tenant) {
        String code = "SEC_CAT_" + NEXT_CODE.incrementAndGet();
        return jdbc.queryForObject("""
                insert into categorias_cardapio
                    (codigo, nome_exibicao, ordem_exibicao, ativo, estabelecimento_id)
                values (?, 'Categoria teste', 1, true, ?) returning id
                """, Integer.class, code, tenant);
    }

    private int produto(UUID tenant) {
        return jdbc.queryForObject("""
                insert into produtos (nome, quantidade_atual, quantidade_minima,
                                      preco_unitario, estabelecimento_id)
                values ('Produto menu', 1, 0, 5.00, ?) returning id
                """, Integer.class, tenant);
    }

    private long item(UUID tenant, int categoryId, Integer productId) {
        String code = "SEC_ITEM_" + NEXT_CODE.incrementAndGet();
        return jdbc.queryForObject("""
                insert into itens_cardapio
                    (codigo, nome, preco_venda, categoria_cardapio_id, tipo_item,
                     ativo, disponivel, ordem_exibicao, produto_vinculado_id, estabelecimento_id)
                values (?, 'Item seguro', 10.00, ?, 'ESTOQUE_DIRETO', true, true, 1, ?, ?)
                returning id
                """, Long.class, code, categoryId, productId, tenant);
    }
}
