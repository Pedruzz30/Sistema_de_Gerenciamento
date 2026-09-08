package integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Fase3MigrationSqlTest {

    @Test
    void sqlDaMigrationCriaDisponibilidadePausasHorariosECanais() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260714_02__fase3_disponibilidade_pausas_horarios.sql"
        ));

        assertTrue(sql.contains("add column if not exists limite_alerta_capacidade integer"));
        assertTrue(sql.contains("create table if not exists pausas_cardapio"));
        assertTrue(sql.contains("create table if not exists regras_horario_cardapio"));
        assertTrue(sql.contains("create table if not exists variacao_canal_disponibilidade"));
        assertTrue(sql.contains("constraint uk_variacao_canal_filial unique"));
        assertTrue(sql.contains("idx_pausas_cardapio_filial_ativas"));
        assertTrue(sql.contains("idx_regras_horario_filial"));
        assertTrue(sql.contains("SOBRECARGA_DA_COZINHA"));
        assertTrue(sql.contains("DELIVERY_PROPRIO"));
    }
}
