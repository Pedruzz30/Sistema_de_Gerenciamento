package integration;

import app.Main;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import repository.UsuarioRepository;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0"
)
class Wave1PersistenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void contextoSobeComTabelasPrincipaisDoPostgresDisponiveis() {
        Set<String> tabelas = Set.copyOf(jdbcTemplate.queryForList(
                """
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                """,
                String.class
        ));

        assertTrue(tabelas.containsAll(Set.of(
                "usuarios",
                "produtos",
                "movimentacoes_estoque",
                "fornecedores",
                "fornecedor_produtos",
                "notas_fiscais",
                "nota_fiscal_itens",
                "cotacoes_mensais",
                "logs_auditoria",
                "caixas",
                "movimentacoes_caixa"
        )));
    }

    @Test
    void colunasCriticasDaMigracaoPersistemNoSchema() {
        assertTrue(colunasDaTabela("usuarios").containsAll(Set.of(
                "ru",
                "cpf",
                "classe_nome",
                "classe_permissoes"
        )));
        assertTrue(colunasDaTabela("movimentacoes_estoque").containsAll(Set.of(
                "descricao",
                "saldo_anterior",
                "saldo_posterior",
                "usuario_responsavel_ru",
                "usuario_responsavel_nome"
        )));
        assertTrue(colunasDaTabela("cotacoes_mensais").containsAll(Set.of(
                "produto_id",
                "mes_referencia",
                "preco_unitario",
                "quantidade_comprada"
        )));
        assertTrue(colunasDaTabela("movimentacoes_caixa").containsAll(Set.of(
                "id",
                "tipo",
                "valor",
                "operador_ru",
                "data_hora"
        )));
    }

    @Test
    void seedDeUsuariosPadraoPermaneceDisponivel() {
        assertTrue(usuarioRepository.listarTodos().stream()
                .anyMatch(usuario -> usuario.isAtivo() && "52998224725".equals(usuario.getCpfBruto())));
    }

    private Set<String> colunasDaTabela(String tabela) {
        List<String> colunas = jdbcTemplate.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = ?
                """,
                String.class,
                tabela
        );
        return Set.copyOf(colunas);
    }
}
