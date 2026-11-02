package integration;

import app.Main;
import app.tenancy.TenantScopedSalesService;
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
class TenantScopedSalesIntegrationTest {
    private static final AtomicLong NEXT = new AtomicLong(86000000000L);
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantScopedSalesService sales;

    @Test
    void gerenteVeApenasVendasDaPropriaLoja() {
        UUID lojaA = loja("Financeiro A");
        UUID lojaB = loja("Financeiro B");
        long ruA = usuario();
        long ruB = usuario();
        vincular(lojaA, ruA, "GERENTE");
        vincular(lojaB, ruB, "PROPRIETARIO");
        long vendaA = venda(lojaA);
        long vendaB = venda(lojaB);

        assertEquals(1, sales.listarVendas(lojaA, ruA).size());
        assertEquals(vendaA, sales.listarVendas(lojaA, ruA).get(0).id());
        assertTrue(sales.buscarVenda(lojaA, ruA, vendaB).isEmpty());
        assertEquals(vendaB, sales.buscarVenda(lojaB, ruB, vendaB).orElseThrow().id());
        assertThrows(SecurityException.class, () -> sales.listarVendas(lojaB, ruA));
    }

    @Test
    void operadorNaoRecebeDadosFinanceirosMesmoSendoMembro() {
        UUID loja = loja("Financeiro Privado");
        long ru = usuario();
        vincular(loja, ru, "OPERADOR");
        long venda = venda(loja);

        assertThrows(SecurityException.class, () -> sales.listarVendas(loja, ru));
        assertThrows(SecurityException.class, () -> sales.buscarVenda(loja, ru, venda));
    }

    @Test
    void vendaLegadaSemTenantNaoApareceNoRelatorio() {
        UUID loja = loja("Financeiro Isolado");
        long ru = usuario();
        vincular(loja, ru, "PROPRIETARIO");
        venda(null);
        assertTrue(sales.listarVendas(loja, ru).isEmpty());
    }

    private UUID loja(String nome) {
        return jdbc.queryForObject("insert into estabelecimentos(nome) values (?) returning id",
                UUID.class, nome);
    }

    private long usuario() {
        return jdbc.queryForObject("""
                insert into usuarios (nome, sobrenome, cpf, senha, ativo, perfil)
                values ('Teste', 'Vendas', ?, 'somente-teste', true, 'OPERADOR')
                returning ru
                """, Long.class, Long.toString(NEXT.incrementAndGet()));
    }

    private void vincular(UUID loja, long ru, String papel) {
        jdbc.update("""
                insert into usuarios_estabelecimentos
                    (estabelecimento_id, usuario_ru, ru_local, papel)
                values (?, ?, 1, ?)
                """, loja, ru, papel);
    }

    private long venda(UUID tenant) {
        return jdbc.queryForObject("""
                insert into vendas
                    (filial_id, numero, status, canal, subtotal, desconto_total,
                     acrescimo_total, valor_total, custo_ingredientes_total,
                     margem_bruta_estimada_total, criada_em, estabelecimento_id)
                values (?, ?, 'RASCUNHO', 'SALAO', 30.00, 0, 0, 30.00, 5.00,
                        25.00, now(), ?) returning id
                """, Long.class, NEXT.incrementAndGet(), "FIN_" + NEXT.incrementAndGet(), tenant);
    }
}
