package integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Fase25MigrationSqlTest {

    @Test
    void sqlDaMigrationEndureceCancelamentosEEstornosParciais() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260714_01__fase25_endurecimento_vendas_cancelamentos.sql"
        ));
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        String runner = Files.readString(Path.of("src/main/java/app/EstoqueDecimalSchemaCompatibilityRunner.java"));

        assertTrue(sql.contains("drop constraint if exists uk_movimentos_estoque_estorno_unico"));
        assertTrue(sql.contains("drop index if exists uk_movimentos_estoque_estorno_unico"));
        assertTrue(sql.contains("create index if not exists idx_movimentos_estoque_estornado"));
        assertTrue(sql.contains("create table if not exists venda_item_consumo_estornos"));
        assertTrue(sql.contains("quantidade_base_estornada numeric(19, 6) not null"));
        assertTrue(sql.contains("ficha_tecnica_versao_observada_id bigint"));
        assertTrue(sql.contains("fk_venda_item_ficha_versao_observada"));
        assertTrue(properties.contains("stockos.schema-compatibility-runner.enabled=${SCHEMA_COMPATIBILITY_RUNNER_ENABLED:false}"));
        assertTrue(runner.contains("${stockos.schema-compatibility-runner.enabled:false}"));
        assertTrue(runner.contains("Ele nao executa DDL"));
        assertTrue(!runner.contains("jdbcTemplate.execute"));
    }
}
