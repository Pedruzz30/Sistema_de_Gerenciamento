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
class TenantInvoiceReferenceIntegrityIntegrationTest {
    private static final AtomicLong NEXT_ID = new AtomicLong(96000000000L);
    @Autowired JdbcTemplate jdbc;

    @Test
    void aceitaNotaEProdutoDaMesmaLoja() {
        UUID tenant = loja("Nota permitida");
        long user = usuario();
        vincular(tenant, user);
        long supplier = fornecedor(tenant);
        int product = produto(tenant);
        long invoice = nota(tenant, supplier, user);
        jdbc.update("""
                insert into nota_fiscal_itens
                    (nota_fiscal_id, produto_id, quantidade, preco_unitario_nota, estabelecimento_id)
                values (?, ?, 2, 5.00, ?)
                """, invoice, product, tenant);
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from nota_fiscal_itens
                where nota_fiscal_id = ? and estabelecimento_id = ?
                """, Integer.class, invoice, tenant));
    }

    @Test
    void rejeitaFornecedorDeOutraLojaNaNota() {
        UUID tenantA = loja("Nota loja A");
        UUID tenantB = loja("Fornecedor loja B");
        long user = usuario();
        vincular(tenantA, user);
        long supplierB = fornecedor(tenantB);
        assertThrows(DataIntegrityViolationException.class,
                () -> nota(tenantA, supplierB, user));
    }

    @Test
    void rejeitaOperadorSemVinculoNaNota() {
        UUID tenant = loja("Nota sem operador vinculado");
        long user = usuario();
        long supplier = fornecedor(tenant);
        assertThrows(DataIntegrityViolationException.class,
                () -> nota(tenant, supplier, user));
    }

    @Test
    void rejeitaProdutoDeOutraLojaEmItemFiscal() {
        UUID tenantA = loja("Nota dos itens A");
        UUID tenantB = loja("Nota dos itens B");
        long user = usuario();
        vincular(tenantA, user);
        long supplierA = fornecedor(tenantA);
        int productB = produto(tenantB);
        long invoiceA = nota(tenantA, supplierA, user);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into nota_fiscal_itens
                    (nota_fiscal_id, produto_id, quantidade, preco_unitario_nota, estabelecimento_id)
                values (?, ?, 1, 5.00, ?)
                """, invoiceA, productB, tenantA));
    }

    @Test
    void rejeitaOmissaoDeTenantEmItemDeNotaDeLoja() {
        UUID tenant = loja("Nota com tenant ausente");
        long user = usuario();
        vincular(tenant, user);
        long supplier = fornecedor(tenant);
        int product = produto(tenant);
        long invoice = nota(tenant, supplier, user);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into nota_fiscal_itens
                    (nota_fiscal_id, produto_id, quantidade, preco_unitario_nota)
                values (?, ?, 1, 5.00)
                """, invoice, product));
    }

    @Test
    void preservaNotaLegadaSemTenant() {
        long user = usuario();
        long supplier = fornecedor(null);
        int product = produto(null);
        long invoice = nota(null, supplier, user);
        jdbc.update("""
                insert into nota_fiscal_itens
                    (nota_fiscal_id, produto_id, quantidade, preco_unitario_nota)
                values (?, ?, 1, 5.00)
                """, invoice, product);
        assertNull(jdbc.queryForObject(
                "select estabelecimento_id from notas_fiscais where id = ?", UUID.class, invoice));
    }

    private UUID loja(String name) {
        return jdbc.queryForObject(
                "insert into estabelecimentos (nome) values (?) returning id", UUID.class, name);
    }
    private long usuario() {
        return jdbc.queryForObject("""
                insert into usuarios (nome, sobrenome, cpf, senha, ativo, perfil)
                values ('Teste', 'Nota', ?, 'apenas-teste', true, 'OPERADOR') returning ru
                """, Long.class, Long.toString(NEXT_ID.incrementAndGet()));
    }
    private void vincular(UUID tenant, long user) {
        jdbc.update("""
                insert into usuarios_estabelecimentos
                    (estabelecimento_id, usuario_ru, ru_local, papel)
                values (?, ?, 1, 'GERENTE')
                """, tenant, user);
    }
    private long fornecedor(UUID tenant) {
        return jdbc.queryForObject("""
                insert into fornecedores (nome, cnpj, ativo, estabelecimento_id)
                values ('Fornecedor Nota', ?, true, ?) returning id
                """, Long.class, String.format("%014d", NEXT_ID.incrementAndGet()), tenant);
    }
    private int produto(UUID tenant) {
        return jdbc.queryForObject("""
                insert into produtos (nome, quantidade_atual, quantidade_minima,
                                      preco_unitario, estabelecimento_id)
                values ('Produto Nota', 3, 1, 5.00, ?) returning id
                """, Integer.class, tenant);
    }
    private long nota(UUID tenant, long supplier, long user) {
        return jdbc.queryForObject("""
                insert into notas_fiscais (fornecedor_id, usuario_responsavel_ru,
                   data_emissao, status, estabelecimento_id)
                values (?, ?, now(), 'PENDENTE', ?) returning id
                """, Long.class, supplier, user, tenant);
    }
}
