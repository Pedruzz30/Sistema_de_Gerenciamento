package integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Fase0MigrationSqlTest {

    @Test
    void sqlDaMigrationReferenciaCamposFinanceirosEIdempotenciaDoModeloJpa() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260713_01__fase0_estoque_decimal.sql"
        ));

        assertTrue(sql.contains("custo_medio_anterior numeric(19, 8)"));
        assertTrue(sql.contains("custo_medio_posterior numeric(19, 8)"));
        assertTrue(sql.contains("valor_estoque_anterior numeric(19, 4)"));
        assertTrue(sql.contains("valor_estoque_posterior numeric(19, 4)"));
        assertTrue(sql.contains("status varchar(20) not null default 'CONCLUIDA'"));
        assertTrue(sql.contains("constraint uk_registros_idempotencia_escopo unique (filial_id, operacao, idempotency_key)"));
        assertTrue(sql.contains("alter column recurso_id drop not null"));
    }
}
