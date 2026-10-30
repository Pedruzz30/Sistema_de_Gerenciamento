package integration;

import app.Main;
import model.Pedido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import repository.JpaPedidoRepository;
import repository.PedidoRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.jpa.show-sql=false", "stockos.seed.demo-menu.enabled=false"})
@Transactional
class MovimentacoesRecentesPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired JpaPedidoRepository jpa;
    @Autowired PedidoRepository repository;

    @Test
    void consultaRecentesOrdenaELimitaNoPostgres() {
        Integer productId = jdbc.queryForObject("""
            INSERT INTO produtos(nome, quantidade_atual, quantidade_minima, preco_unitario,
                                 controlado_por_estoque, ativo)
            VALUES ('stockos-recentes-ci', 500, 1, 10, true, true)
            RETURNING id
            """, Integer.class);
        assertNotNull(productId);

        // Future synthetic timestamps guarantee that this fixture sorts ahead of default seed.
        for (int i = 0; i < 120; i++) {
            jdbc.update("""
                INSERT INTO movimentacoes_estoque(produto_id, quantidade, tipo_movimentacao, data_hora,
                        descricao, saldo_anterior, saldo_posterior)
                VALUES (?, 1, 'SAIDA', ?, ?, 500, 499)
                """, productId, LocalDateTime.of(2099, 1, 1, 0, 0).plusMinutes(i), "CI-" + i);
        }

        List<Pedido> rows = jpa.findAllByOrderByDataHoraDescIdDesc(PageRequest.of(0, 100));
        assertEquals(100, rows.size());
        assertEquals(productId.intValue(), rows.get(0).getProduto().getId());
        assertEquals("CI-119", rows.get(0).getDescricao());
        assertEquals("CI-20", rows.get(99).getDescricao());

        List<Pedido> viaPort = repository.listarRecentes(100);
        assertEquals(rows.stream().map(Pedido::getId).toList(),
                viaPort.stream().map(Pedido::getId).toList());
        assertThrows(IllegalArgumentException.class, () -> repository.listarRecentes(0));
        assertThrows(IllegalArgumentException.class, () -> repository.listarRecentes(501));
    }
}
