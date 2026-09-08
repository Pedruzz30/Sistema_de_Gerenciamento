package integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Fase4MigrationSqlTest {

    @Test
    void sqlDaMigrationCriaModificadoresSnapshotsEImpactos() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260714_03__fase4_modificadores_composicao.sql"
        ));

        assertTrue(sql.contains("create table if not exists grupos_modificadores"));
        assertTrue(sql.contains("create table if not exists opcoes_modificadores"));
        assertTrue(sql.contains("create table if not exists variacao_grupos_modificadores"));
        assertTrue(sql.contains("create table if not exists opcoes_modificadores_componentes"));
        assertTrue(sql.contains("create table if not exists incompatibilidades_opcoes_modificadores"));
        assertTrue(sql.contains("add column if not exists configuracao_modificadores_hash_observada"));
        assertTrue(sql.contains("create table if not exists venda_item_modificadores"));
        assertTrue(sql.contains("create table if not exists venda_item_modificador_impactos"));
        assertTrue(sql.contains("MODIFICADOR_ADICAO"));
        assertTrue(sql.contains("MODIFICADOR_REMOCAO"));
        assertTrue(sql.contains("MODIFICADOR_SUBSTITUICAO"));
        assertTrue(sql.contains("constraint uk_variacao_grupo_modificador unique"));
    }
}
