package integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Confere o conteúdo das migrações do Caixa PDV de varejo (04/11/2026). */
class VarejoPdvMigrationSqlNovoTest {

    @Test
    void saldoUnicoCopiaSaldosLegadosEEspelhaODecimal() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20261104_01__saldo_unico_estoque.sql"));
        assertTrue(sql.contains("insert into estoques_produto"));
        assertTrue(sql.contains("not exists"));
        assertTrue(sql.contains("nota_fiscal_itens"));
        assertTrue(sql.contains("update produtos p"));
        assertTrue(sql.contains("greatest(floor(e.quantidade_atual), 0)::integer"));
    }

    @Test
    void caixaGanhaFormasDePagamentoEstornoSequenceECodigos() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20261104_02__caixa_pagamentos_codigos_venda.sql"));
        assertTrue(sql.contains("add column forma_pagamento varchar(20)"));
        assertTrue(sql.contains("'ESTORNO_VENDA'"));
        assertTrue(sql.contains("create sequence if not exists movimentacoes_caixa_id_seq"));
        assertTrue(sql.contains("alter column id set default nextval('movimentacoes_caixa_id_seq')"));
        assertTrue(sql.contains("create table venda_pagamentos"));
        assertTrue(sql.contains("ck_venda_pagamentos_troco_dinheiro"));
        assertTrue(sql.contains("create table codigos_venda"));
        assertTrue(sql.contains("create unique index uk_codigos_venda_padrao_ativo"));
    }
}
