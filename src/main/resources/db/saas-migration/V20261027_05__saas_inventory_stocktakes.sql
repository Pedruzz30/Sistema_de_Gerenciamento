-- Physical stocktake corrections. Independent of sales, returns and refunds.
-- Audited, scoped by company/branch and idempotent per company.
create table stockos_private.ajustes_inventario (
  id uuid primary key default gen_random_uuid(),
  empresa_id uuid not null,
  filial_id uuid not null,
  produto_id uuid not null,
  estoque_anterior integer not null check (estoque_anterior >= 0),
  estoque_contado integer not null check (estoque_contado >= 0),
  diferenca integer generated always as (estoque_contado - estoque_anterior) stored,
  motivo varchar(240) not null check (length(trim(motivo)) >= 8),
  idempotency_key varchar(120) not null,
  registrado_por uuid not null,
  registrado_em timestamptz not null default now(),
  foreign key (empresa_id,filial_id,produto_id)
    references stockos_private.produtos (empresa_id,filial_id,id),
  unique (empresa_id,idempotency_key)
);
create index ix_stockos_ajustes_inventario_filial
  on stockos_private.ajustes_inventario (empresa_id,filial_id,registrado_em desc,id desc);
create index ix_stockos_ajustes_inventario_produto
  on stockos_private.ajustes_inventario (empresa_id,filial_id,produto_id,registrado_em desc);
alter table stockos_private.ajustes_inventario enable row level security;
