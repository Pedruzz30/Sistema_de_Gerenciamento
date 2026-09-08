package integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Fase5MigrationSqlTest {

    @Test
    void sqlDaMigrationCriaEstruturaDeComposicaoMultissabor() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260715_01__fase5_composicao_multissabor.sql"
        ));

        assertTrue(sql.contains("create table configuracoes_composicao_multipla"));
        assertTrue(sql.contains("create table configuracao_composicao_multipla_partes"));
        assertTrue(sql.contains("create table venda_item_partes"));
        assertTrue(sql.contains("add column composicao_multipla"));
        assertTrue(sql.contains("add column venda_item_parte_id"));
        assertTrue(sql.contains("politica_preco_composicao"));
        assertTrue(sql.contains("MAIOR_PRECO"));
        assertTrue(sql.contains("MEDIA_ARITMETICA"));
        assertTrue(sql.contains("MEDIA_PONDERADA"));
        assertTrue(sql.contains("SOMA_PROPORCIONAL"));
        assertTrue(sql.contains("PRECO_FIXO_DA_VARIACAO"));
        assertTrue(sql.contains("numeric(19, 6)"));
        assertTrue(sql.contains("constraint uk_config_composicao_multipla_variacao unique"));
        assertTrue(sql.contains("constraint uk_config_composicao_multipla_parte unique"));
        assertTrue(sql.contains("venda_item_id bigint not null references venda_itens(id)"));
        assertTrue(sql.contains("add column venda_item_parte_id bigint references venda_item_partes(id)"));
    }
}
