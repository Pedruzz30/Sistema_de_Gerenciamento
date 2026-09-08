-- Fase 2.5 - endurecimento de venda, cancelamentos e estornos parciais.
-- Esta migration remove a restricao 1:1 entre movimento original e estorno,
-- adiciona rastreabilidade por consumo individual e preserva a ficha observada
-- durante o rascunho da venda.

alter table movimentos_estoque_decimal
    drop constraint if exists uk_movimentos_estoque_estorno_unico;

drop index if exists uk_movimentos_estoque_estorno_unico;

create index if not exists idx_movimentos_estoque_estornado
    on movimentos_estoque_decimal (movimento_estornado_id)
    where movimento_estornado_id is not null;

alter table venda_itens
    add column if not exists ficha_tecnica_observada_id bigint;

alter table venda_itens
    add column if not exists ficha_tecnica_versao_observada_id bigint;

alter table venda_itens
    add column if not exists numero_versao_ficha_observada integer;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_venda_item_ficha_observada'
    ) then
        alter table venda_itens
            add constraint fk_venda_item_ficha_observada
            foreign key (ficha_tecnica_observada_id)
            references fichas_tecnicas(id);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_venda_item_ficha_versao_observada'
    ) then
        alter table venda_itens
            add constraint fk_venda_item_ficha_versao_observada
            foreign key (ficha_tecnica_versao_observada_id)
            references fichas_tecnicas_versoes(id);
    end if;
end $$;

create index if not exists idx_venda_itens_ficha_observada
    on venda_itens (ficha_tecnica_versao_observada_id);

create table if not exists venda_item_consumo_estornos (
    id bigserial primary key,
    venda_item_consumo_id bigint not null references venda_item_consumos(id),
    movimento_estoque_id bigint not null references movimentos_estoque_decimal(id),
    quantidade_base_estornada numeric(19, 6) not null,
    custo_unitario_snapshot numeric(19, 8) not null,
    valor_estornado numeric(19, 4) not null,
    criado_em timestamp not null,
    constraint ck_venda_consumo_estorno_qtd check (quantidade_base_estornada > 0),
    constraint ck_venda_consumo_estorno_custo check (
        custo_unitario_snapshot >= 0
        and valor_estornado >= 0
    )
);

create index if not exists idx_venda_consumo_estornos_consumo
    on venda_item_consumo_estornos (venda_item_consumo_id);

create index if not exists idx_venda_consumo_estornos_movimento
    on venda_item_consumo_estornos (movimento_estoque_id);
