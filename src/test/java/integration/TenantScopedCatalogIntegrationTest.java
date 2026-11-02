package integration;

import app.Main;
import app.tenancy.TenantScopedCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TenantScopedCatalogIntegrationTest {
    private static final AtomicLong TEST_CPF = new AtomicLong(85_000_000_000L);

    @Autowired JdbcTemplate jdbc;
    @Autowired TenantScopedCatalogService catalogo;

    @Test
    void nuncaListaOuRenomeiaProdutosDeOutroEstabelecimento() {
        long ruA = usuario();
        long ruB = usuario();
        UUID lojaA = loja("Estabelecimento A");
        UUID lojaB = loja("Estabelecimento B");
        vincular(lojaA, ruA, "PROPRIETARIO");
        vincular(lojaB, ruB, "GERENTE");

        int produtoA = catalogo.cadastrarProduto(lojaA, ruA, "Refrigerante A", 10, 1, new BigDecimal("8.00"));
        int produtoB = catalogo.cadastrarProduto(lojaB, ruB, "Refrigerante B", 20, 1, new BigDecimal("9.00"));

        assertEquals(1, catalogo.listarProdutos(lojaA, ruA).size());
        assertEquals(1, catalogo.listarProdutos(lojaB, ruB).size());
        assertEquals("Refrigerante A", catalogo.buscarProduto(lojaA, ruA, produtoA).orElseThrow().nome());
        assertTrue(catalogo.buscarProduto(lojaA, ruA, produtoB).isEmpty());
        assertThrows(SecurityException.class,
                () -> catalogo.renomearProduto(lojaA, ruA, produtoB, "Ataque"));
        assertEquals("Refrigerante B", catalogo.buscarProduto(lojaB, ruB, produtoB).orElseThrow().nome());
        assertThrows(SecurityException.class, () -> catalogo.listarProdutos(lojaB, ruA));
        assertThrows(SecurityException.class, () -> catalogo.cadastrarProduto(lojaB, ruA, "Invasao", 1, 0, new BigDecimal("2.00")));
        assertThrows(SecurityException.class, () -> catalogo.renomearProduto(lojaB, ruA, produtoB, "Ataque"));
    }

    @Test
    void operadorNaoPodeEditarEProdutosLegadosSemTenantNaoSaoVisiveis() {
        long ru = usuario();
        UUID loja = loja("Loja Protegida");
        vincular(loja, ru, "OPERADOR");
        assertThrows(SecurityException.class,
                () -> catalogo.cadastrarProduto(loja, ru, "Bloqueado", 1, 1, new BigDecimal("10.00")));
        int legacyId = jdbc.queryForObject("""
                insert into produtos (nome, quantidade_atual, quantidade_minima, preco_unitario)
                values ('Produto legado sem dono', 1, 1, 2.00)
                returning id
                """, Integer.class);
        assertTrue(catalogo.buscarProduto(loja, ru, legacyId).isEmpty());
        assertTrue(catalogo.listarProdutos(loja, ru).isEmpty());
    }

    private long usuario() {
        return jdbc.queryForObject("""
                insert into usuarios (nome, sobrenome, cpf, senha, ativo, perfil)
                values ('Teste', 'Tenant', ?, 'somente-testes', true, 'OPERADOR')
                returning ru
                """, Long.class, Long.toString(TEST_CPF.incrementAndGet()));
    }
    private UUID loja(String nome) {
        return jdbc.queryForObject("insert into estabelecimentos (nome) values (?) returning id", UUID.class, nome);
    }
    private void vincular(UUID loja, long usuario, String papel) {
        jdbc.update("""
                insert into usuarios_estabelecimentos
                    (estabelecimento_id, usuario_ru, ru_local, papel)
                values (?, ?, ?, ?)
                """, loja, usuario, 1, papel);
    }
}
