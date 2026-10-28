-- StockOS: physical goods returns after a sale, separate from financial refunds.
-- Append-only auditable events. Returned items can be restocked OR discarded.
-- Financial refunds never increase sellable stock automatically.
create table stockos_private.devolucoes_estoque (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    comanda_id uuid not null,
    comanda_item_id uuid not null,
    produto_id uuid not null,
    quantidade integer not null check (quantidade > 0),
    destino varchar(16) not null check (destino in ('REESTOQUE','DESCARTE')),
    motivo varchar(240) not null check (length(trim(motivo)) >= 8),
    idempotency_key varchar(120) not null,
    registrado_por uuid not null,
    registrado_em timestamptz not null default now(),
    foreign key (empresa_id,filial_id,comanda_id,comanda_item_id)
        references stockos_private.comanda_itens (empresa_id,filial_id,comanda_id,id),
    foreign key (empresa_id,filial_id,produto_id)
        references stockos_private.produtos (empresa_id,filial_id,id),
    unique (empresa_id,idempotency_key)
);
create index ix_stockos_devolucoes_estoque_item
    on stockos_private.devolucoes_estoque
       (empresa_id,filial_id,comanda_item_id,registrado_em);
alter table stockos_private.devolucoes_estoque enable row level security;
