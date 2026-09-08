package integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Fase1MigrationSqlTest {

    @Test
    void sqlDaMigrationReferenciaVariacoesFichasVersionamentoESnapshots() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260713_02__fase1_variacoes_fichas.sql"
        ));

        assertTrue(sql.contains("create table if not exists variacoes_item_cardapio"));
        assertTrue(sql.contains("constraint uk_variacao_item_codigo unique (item_cardapio_id, codigo)"));
        assertTrue(sql.contains("uk_variacao_padrao_ativa_item"));
        assertTrue(sql.contains("create table if not exists fichas_tecnicas"));
        assertTrue(sql.contains("create table if not exists fichas_tecnicas_versoes"));
        assertTrue(sql.contains("constraint uk_ficha_versao_numero unique (ficha_tecnica_id, numero_versao)"));
        assertTrue(sql.contains("uk_ficha_publicada_vigente"));
        assertTrue(sql.contains("uk_ficha_rascunho_ativo"));
        assertTrue(sql.contains("quantidade_bruta_base numeric(19,6) not null"));
        assertTrue(sql.contains("create table if not exists fichas_tecnicas_custo_snapshot"));
        assertTrue(sql.contains("custo_medio_unitario numeric(19,8) not null"));
    }
}
