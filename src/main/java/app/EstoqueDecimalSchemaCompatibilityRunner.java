package app;

import model.TipoMovimentoEstoque;
import model.OrigemComposicaoVenda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Validador temporario de compatibilidade para bancos criados antes do Flyway.
 *
 * A partir da Fase 4.6 este componente nao cria, remove ou altera estrutura.
 * Qualquer DDL estrutural deve estar em migration versionada. Quando habilitado,
 * ele apenas detecta divergencias conhecidas e falha de forma explicita se a
 * propriedade {@code stockos.schema-compatibility-runner.fail-on-mismatch} estiver ativa.
 */
@Component
public class EstoqueDecimalSchemaCompatibilityRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(EstoqueDecimalSchemaCompatibilityRunner.class);
    private static final String TABELA_MOVIMENTOS = "movimentos_estoque_decimal";
    private static final String TABELA_CONSUMOS_VENDA = "venda_item_consumos";
    private static final String CONSTRAINT_TIPO = "movimentos_estoque_decimal_tipo_check";
    private static final String CONSTRAINT_ESTORNO_UNICO = "uk_movimentos_estoque_estorno_unico";
    private static final String CONSTRAINT_CONSUMO_ORIGEM = "ck_venda_consumo_origem";

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final boolean failOnMismatch;

    public EstoqueDecimalSchemaCompatibilityRunner(JdbcTemplate jdbcTemplate,
                                                   @Value("${stockos.schema-compatibility-runner.enabled:false}") boolean enabled,
                                                   @Value("${stockos.schema-compatibility-runner.fail-on-mismatch:true}") boolean failOnMismatch) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.failOnMismatch = failOnMismatch;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            LOGGER.info("Runner temporario de compatibilidade de schema decimal desativado por configuracao.");
            return;
        }
        LOGGER.warn("Validador temporario de compatibilidade de schema decimal ativo. Ele nao executa DDL.");
        List<String> problemas = new java.util.ArrayList<>();
        if (tabelaExiste(TABELA_MOVIMENTOS)) {
            validarCheckTipoMovimento(problemas);
            validarUniqueEstornoLegada(problemas);
        }
        if (tabelaExiste(TABELA_CONSUMOS_VENDA)) {
            validarCheckOrigemConsumoVenda(problemas);
        }
        if (problemas.isEmpty()) {
            LOGGER.info("Compatibilidade estrutural decimal validada sem divergencias conhecidas.");
            return;
        }
        String mensagem = "Schema incompatível com Fase 4.6: " + String.join("; ", problemas);
        if (failOnMismatch) {
            throw new IllegalStateException(mensagem);
        }
        LOGGER.warn(mensagem);
    }

    private boolean tabelaExiste(String tabela) {
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = current_schema() and table_name = ?",
                Integer.class,
                tabela
        );
        return total != null && total > 0;
    }

    private void validarCheckTipoMovimento(List<String> problemas) {
        if (!checkTipoMovimentoAtualizado()) {
            problemas.add("check " + CONSTRAINT_TIPO + " ausente ou sem todos os tipos de movimento");
        }
    }

    private boolean checkTipoMovimentoAtualizado() {
        List<String> checks = jdbcTemplate.queryForList(
                """
                select cc.check_clause
                from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on cc.constraint_schema = tc.constraint_schema
                 and cc.constraint_name = tc.constraint_name
                where tc.table_schema = current_schema()
                  and tc.table_name = ?
                  and tc.constraint_name = ?
                """,
                String.class,
                TABELA_MOVIMENTOS,
                CONSTRAINT_TIPO
        );
        if (checks.isEmpty()) {
            return false;
        }
        String check = checks.get(0);
        return Arrays.stream(TipoMovimentoEstoque.values()).allMatch(tipo -> check.contains(tipo.name()));
    }

    private void validarUniqueEstornoLegada(List<String> problemas) {
        if (constraintExiste(TABELA_MOVIMENTOS, CONSTRAINT_ESTORNO_UNICO)) {
            problemas.add("constraint unica legada " + CONSTRAINT_ESTORNO_UNICO + " ainda existe");
        }
        if (indiceExiste(CONSTRAINT_ESTORNO_UNICO)) {
            problemas.add("indice unico legado " + CONSTRAINT_ESTORNO_UNICO + " ainda existe");
        }
    }

    private void validarCheckOrigemConsumoVenda(List<String> problemas) {
        if (!checkOrigemConsumoVendaAtualizado()) {
            problemas.add("check " + CONSTRAINT_CONSUMO_ORIGEM + " ausente ou sem todas as origens de consumo");
        }
    }

    private boolean checkOrigemConsumoVendaAtualizado() {
        List<String> checks = listarCheckClausesDaColuna(TABELA_CONSUMOS_VENDA, "origem_composicao");
        if (checks.isEmpty()) {
            return false;
        }
        return checks.stream().allMatch(check ->
                Arrays.stream(OrigemComposicaoVenda.values()).allMatch(origem -> check.contains(origem.name())));
    }

    private List<String> listarCheckClausesDaColuna(String tabela, String coluna) {
        return jdbcTemplate.queryForList(
                """
                select cc.check_clause
                from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on cc.constraint_schema = tc.constraint_schema
                 and cc.constraint_name = tc.constraint_name
                where tc.table_schema = current_schema()
                  and tc.table_name = ?
                  and tc.constraint_type = 'CHECK'
                  and cc.check_clause like ?
                """,
                String.class,
                tabela,
                "%" + coluna + "%"
        );
    }

    private boolean constraintExiste(String tabela, String constraint) {
        Integer total = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where table_schema = current_schema()
                  and table_name = ?
                  and constraint_name = ?
                """,
                Integer.class,
                tabela,
                constraint
        );
        return total != null && total > 0;
    }

    private boolean indiceExiste(String indice) {
        Integer total = jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_indexes
                where schemaname = current_schema()
                  and indexname = ?
                """,
                Integer.class,
                indice
        );
        return total != null && total > 0;
    }
}
