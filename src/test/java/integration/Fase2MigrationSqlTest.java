package integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Fase2MigrationSqlTest {

    @Test
    void sqlDaMigrationReferenciaVendasSnapshotsEConsumos() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260713_03__fase2_vendas_snapshots_consumos.sql"
        ));

        assertTrue(sql.contains("create table if not exists vendas"));
        assertTrue(sql.contains("create table if not exists venda_itens"));
        assertTrue(sql.contains("create table if not exists venda_item_consumos"));
        assertTrue(sql.contains("quantidade_produto_direto_base numeric(19, 6)"));
        assertTrue(sql.contains("ficha_tecnica_versao_id bigint references fichas_tecnicas_versoes(id)"));
        assertTrue(sql.contains("movimento_estoque_id bigint references movimentos_estoque_decimal(id)"));
        assertTrue(sql.contains("idx_vendas_filial_status_criada"));
        assertTrue(sql.contains("SAIDA_VENDA"));
        assertTrue(sql.contains("ESTORNO_VENDA"));
    }
}
