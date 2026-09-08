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
                "movimentacoes_caixa",
                "fechamentos_caixa"
        )));

        assertTrue(tabelas.containsAll(Set.of(
                "unidades_medida",
                "embalagens_produto",
                "estoques_produto",
                "compras_estoque",
                "itens_compra_estoque",
                "movimentos_estoque_decimal",
                "registros_idempotencia",
                "vendas",
                "venda_itens",
                "venda_item_consumos",
                "venda_item_consumo_estornos",
                "pausas_cardapio",
                "regras_horario_cardapio",
                "variacao_canal_disponibilidade",
                "grupos_modificadores",
                "opcoes_modificadores",
                "variacao_grupos_modificadores",
                "opcoes_modificadores_componentes",
                "incompatibilidades_opcoes_modificadores",
                "venda_item_modificadores",
                "venda_item_modificador_impactos"
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
        assertTrue(colunasDaTabela("fechamentos_caixa").containsAll(Set.of(
                "id",
                "caixa_id",
                "numero_caixa",
                "aberto_por",
                "fechado_por",
                "valor_sistema",
                "valor_contado",
                "divergencia",
                "observacao",
                "timestamp_fechamento"
        )));
        assertTrue(colunasDaTabela("produtos").containsAll(Set.of(
                "unidade_base_id",
                "politica_falta",
                "ativo"
        )));
        assertTrue(colunasDaTabela("estoques_produto").containsAll(Set.of(
                "filial_id",
                "produto_id",
                "quantidade_atual",
                "quantidade_reservada",
                "custo_medio",
                "version",
                "atualizado_em"
        )));
        assertTrue(colunasDaTabela("movimentos_estoque_decimal").containsAll(Set.of(
                "filial_id",
                "produto_id",
                "tipo",
                "quantidade_base",
                "saldo_anterior",
                "saldo_posterior",
                "custo_unitario",
                "custo_total",
                "origem_tipo",
                "origem_id",
                "usuario_id",
                "motivo",
                "idempotency_key",
                "movimento_estornado_id",
                "criado_em"
        )));
        assertTrue(colunasDaTabela("venda_itens").containsAll(Set.of(
                "ficha_tecnica_observada_id",
                "ficha_tecnica_versao_observada_id",
                "numero_versao_ficha_observada",
                "configuracao_modificadores_hash_observada"
        )));
        assertTrue(colunasDaTabela("venda_item_consumo_estornos").containsAll(Set.of(
                "venda_item_consumo_id",
                "movimento_estoque_id",
                "quantidade_base_estornada",
                "custo_unitario_snapshot",
                "valor_estornado",
                "criado_em"
        )));
        assertTrue(colunasDaTabela("variacoes_item_cardapio").contains("limite_alerta_capacidade"));
        assertTrue(colunasDaTabela("pausas_cardapio").containsAll(Set.of(
                "filial_id",
                "item_cardapio_id",
                "variacao_item_cardapio_id",
                "canal",
                "inicio",
                "fim",
                "motivo_codigo",
                "ativa",
                "version"
        )));
        assertTrue(colunasDaTabela("regras_horario_cardapio").containsAll(Set.of(
                "filial_id",
                "item_cardapio_id",
                "variacao_item_cardapio_id",
                "canal",
                "dia_semana",
                "hora_inicio",
                "hora_fim",
                "ativa",
                "version"
        )));
        assertTrue(colunasDaTabela("variacao_canal_disponibilidade").containsAll(Set.of(
                "variacao_item_cardapio_id",
                "filial_id",
                "canal",
                "permitida",
                "criado_em",
                "atualizado_em",
                "version"
        )));
        assertTrue(colunasDaTabela("grupos_modificadores").containsAll(Set.of(
                "nome",
                "codigo",
                "tipo_selecao",
                "obrigatorio",
                "quantidade_minima",
                "quantidade_maxima",
                "permite_repeticao",
                "ativo",
                "version"
        )));
        assertTrue(colunasDaTabela("opcoes_modificadores").containsAll(Set.of(
                "grupo_modificador_id",
                "codigo",
                "acrescimo_preco",
                "quantidade_maxima_por_item",
                "selecionada_por_padrao",
                "ativa",
                "version"
        )));
        assertTrue(colunasDaTabela("venda_item_modificadores").containsAll(Set.of(
                "venda_item_id",
                "grupo_modificador_id",
                "opcao_modificador_id",
                "nome_grupo_snapshot",
                "nome_opcao_snapshot",
                "quantidade_selecionada",
                "acrescimo_total_snapshot"
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
