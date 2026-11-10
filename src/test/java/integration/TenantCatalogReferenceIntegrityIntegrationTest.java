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
class TenantCatalogReferenceIntegrityIntegrationTest {
    private static final AtomicLong CNPJ = new AtomicLong(91000000000000L);

    @Autowired JdbcTemplate jdbc;

    @Test
    void permiteAssociacaoDeProdutoEFornecedorDaMesmaLoja() {
        UUID tenant = loja("Loja dos fornecedores");
        long supplier = fornecedor(tenant);
        int product = produto(tenant);
        jdbc.update("""
                insert into fornecedor_produtos (estabelecimento_id, fornecedor_id, produto_id)
                values (?, ?, ?)
                """, tenant, supplier, product);
        Integer count = jdbc.queryForObject("""
                select count(*) from fornecedor_produtos
                where estabelecimento_id = ? and fornecedor_id = ? and produto_id = ?
                """, Integer.class, tenant, supplier, product);
        assertEquals(1, count);
    }

    @Test
    void bloqueiaVinculoComProdutoDeOutraLoja() {
        UUID lojaA = loja("Fornecedor A");
        UUID lojaB = loja("Produto B");
        long supplierA = fornecedor(lojaA);
        int productB = produto(lojaB);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        insert into fornecedor_produtos (estabelecimento_id, fornecedor_id, produto_id)
                        values (?, ?, ?)
                        """, lojaA, supplierA, productB));
    }

    @Test
    void bloqueiaOmissaoDoTenantNoVinculoNovo() {
        UUID tenant = loja("Loja com vinculo");
        long supplier = fornecedor(tenant);
        int product = produto(tenant);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        insert into fornecedor_produtos (fornecedor_id, produto_id)
                        values (?, ?)
                        """, supplier, product));
    }

    @Test
    void bloqueiaApropriacaoDeProdutoLegado() {
        UUID tenant = loja("Loja que tenta apropriar");
        int legacy = produto(null);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("update produtos set estabelecimento_id = ? where id = ?", tenant, legacy));
    }

    @Test
    void bloqueiaAlteracaoDaLojaDeFornecedor() {
        UUID lojaA = loja("Fornecedor original");
        UUID lojaB = loja("Fornecedor destino");
        long supplier = fornecedor(lojaA);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("update fornecedores set estabelecimento_id = ? where id = ?", lojaB, supplier));
    }

    @Test
    void preservaVinculosLegadosSemProprietario() {
        long supplier = fornecedor(null);
        int product = produto(null);
        jdbc.update("insert into fornecedor_produtos (fornecedor_id, produto_id) values (?, ?)",
                supplier, product);
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from fornecedor_produtos
                where fornecedor_id = ? and produto_id = ? and estabelecimento_id is null
                """, Integer.class, supplier, product));
    }

    private UUID loja(String nome) {
        return jdbc.queryForObject("insert into estabelecimentos (nome) values (?) returning id",
                UUID.class, nome);
    }

    private long fornecedor(UUID tenant) {
        return jdbc.queryForObject("""
                insert into fornecedores (nome, cnpj, ativo, estabelecimento_id)
                values ('Fornecedor Teste', ?, true, ?) returning id
                """, Long.class, Long.toString(CNPJ.incrementAndGet()), tenant);
    }

    private int produto(UUID tenant) {
        return jdbc.queryForObject("""
                insert into produtos (nome, quantidade_atual, quantidade_minima,
                                      preco_unitario, estabelecimento_id)
                values ('Produto Teste', 1, 0, 3.00, ?) returning id
                """, Integer.class, tenant);
    }
}
