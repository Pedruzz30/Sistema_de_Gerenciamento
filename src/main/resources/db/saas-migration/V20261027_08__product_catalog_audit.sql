-- Safe metadata updates: stock balances are never writable via catalog edits.
alter table stockos_private.produtos
  add column versao_cadastro bigint not null default 0;
create table stockos_private.produtos_auditoria (
  id bigint generated always as identity primary key,
  empresa_id uuid not null,
  filial_id uuid not null,
  produto_id uuid not null,
  codigo_anterior varchar(60) not null,
  codigo_novo varchar(60) not null,
  nome_anterior varchar(160) not null,
  nome_novo varchar(160) not null,
  preco_anterior numeric(19,2) not null,
  preco_novo numeric(19,2) not null,
  ativo_anterior boolean not null,
  ativo_novo boolean not null,
  versao_nova bigint not null,
  registrado_por uuid not null,
  registrado_em timestamptz not null default now(),
  foreign key (empresa_id,filial_id,produto_id)
    references stockos_private.produtos(empresa_id,filial_id,id)
);
create index ix_stockos_produtos_auditoria
  on stockos_private.produtos_auditoria (empresa_id,filial_id,produto_id,registrado_em desc);
alter table stockos_private.produtos_auditoria enable row level security;
