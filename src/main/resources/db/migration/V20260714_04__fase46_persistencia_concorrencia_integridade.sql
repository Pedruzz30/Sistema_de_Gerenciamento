-- Fase 4.6 - estabilizacao de persistencia, concorrencia e integridade operacional.
-- Estruturas que antes dependiam do runner temporario passam a ser responsabilidade do Flyway.

alter table movimentos_estoque_decimal
    drop constraint if exists uk_movimentos_estoque_estorno_unico;

drop index if exists uk_movimentos_estoque_estorno_unico;

alter table movimentos_estoque_decimal
    drop constraint if exists movimentos_estoque_decimal_tipo_check;

alter table movimentos_estoque_decimal
    add constraint movimentos_estoque_decimal_tipo_check
    check (tipo in (
        'ENTRADA_COMPRA',
        'DEVOLUCAO_COMPRA',
        'AJUSTE_POSITIVO',
        'AJUSTE_NEGATIVO',
        'ESTORNO_MOVIMENTO',
        'SAIDA_VENDA',
        'ESTORNO_VENDA'
    ));

alter table venda_item_consumos
    drop constraint if exists ck_venda_consumo_origem;

alter table venda_item_consumos
    add constraint ck_venda_consumo_origem
    check (origem_composicao in (
        'PRODUTO_DIRETO',
        'FICHA_TECNICA',
        'MODIFICADOR_ADICAO',
        'MODIFICADOR_REMOCAO',
        'MODIFICADOR_SUBSTITUICAO'
    ));

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_venda_item_modificador_impacto_valor'
    ) then
        alter table venda_item_modificador_impactos
            add constraint ck_venda_item_modificador_impacto_valor
            check (
                (quantidade_delta_base < 0 and valor_impacto_snapshot <= 0)
                or
                (quantidade_delta_base > 0 and valor_impacto_snapshot >= 0)
            );
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_vendas_margem_nao_nula'
    ) then
        alter table vendas
            add constraint ck_vendas_margem_nao_nula
            check (margem_bruta_estimada_total is not null);
    end if;
end $$;

create index if not exists idx_registros_idempotencia_escopo_status
    on registros_idempotencia(filial_id, operacao, idempotency_key, status);

create index if not exists idx_venda_itens_venda_status
    on venda_itens(venda_id, status, id);
