-- First isolated SaaS storage; NEVER exposed through Supabase Data API.
-- Separate from legacy public product/order tables until a verified migration is planned.
create schema if not exists stockos_private;
revoke all on schema stockos_private from public;

create table stockos_private.empresas (
    id uuid primary key default gen_random_uuid(),
    nome varchar(160) not null,
    ativa boolean not null default true,
    criada_em timestamptz not null default now()
);

create table stockos_private.filiais (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null references stockos_private.empresas(id),
    nome varchar(160) not null,
    ativa boolean not null default true,
    unique (empresa_id, id)
);

create table stockos_private.membros (
    empresa_id uuid not null references stockos_private.empresas(id),
    auth_user_id uuid not null,
    papel varchar(24) not null check (papel in ('PROPRIETARIO', 'GERENTE', 'ATENDENTE')),
    ativo boolean not null default true,
    primary key (empresa_id, auth_user_id)
);
create index ix_stockos_membros_usuario on stockos_private.membros (auth_user_id, ativo);

create table stockos_private.produtos (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    codigo varchar(60) not null,
    nome varchar(160) not null,
    preco_unitario numeric(19,2) not null check (preco_unitario >= 0),
    estoque_atual integer not null check (estoque_atual >= 0),
    ativo boolean not null default true,
    foreign key (empresa_id, filial_id)
      references stockos_private.filiais (empresa_id, id),
    unique (filial_id, codigo),
    unique (empresa_id, filial_id, id)
);

create table stockos_private.comandas (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    numero integer not null check (numero between 1 and 9999),
    status varchar(16) not null default 'ABERTA'
      check (status in ('ABERTA', 'FECHADA', 'CANCELADA')),
    aberta_por uuid not null,
    aberta_em timestamptz not null default now(),
    fechada_em timestamptz,
    foreign key (empresa_id, filial_id)
      references stockos_private.filiais (empresa_id, id),
    unique (empresa_id, filial_id, id)
);
create unique index uk_stockos_comanda_aberta_numero
  on stockos_private.comandas (filial_id, numero) where status = 'ABERTA';
create index ix_stockos_comandas_status
  on stockos_private.comandas (empresa_id, filial_id, status, aberta_em desc);

create table stockos_private.comanda_itens (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    comanda_id uuid not null,
    produto_id uuid not null,
    nome_snapshot varchar(160) not null,
    quantidade integer not null check (quantidade > 0),
    preco_unitario_snapshot numeric(19,2) not null check (preco_unitario_snapshot >= 0),
    idempotency_key varchar(120) not null,
    criado_por uuid not null,
    criado_em timestamptz not null default now(),
    cancelado_por uuid,
    cancelado_em timestamptz,
    foreign key (empresa_id, filial_id, comanda_id)
      references stockos_private.comandas (empresa_id, filial_id, id),
    foreign key (empresa_id, filial_id, produto_id)
      references stockos_private.produtos (empresa_id, filial_id, id),
    unique (empresa_id, idempotency_key),
    unique (empresa_id, filial_id, comanda_id, id)
);
create index ix_stockos_comanda_itens
  on stockos_private.comanda_itens (empresa_id, filial_id, comanda_id);

create table stockos_private.pagamentos (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    comanda_id uuid not null,
    modalidade varchar(16) not null check (modalidade in ('DINHEIRO', 'PIX', 'DEBITO', 'CREDITO')),
    valor numeric(19,2) not null check (valor > 0),
    idempotency_key varchar(120) not null,
    registrado_por uuid not null,
    registrado_em timestamptz not null default now(),
    foreign key (empresa_id, filial_id, comanda_id)
      references stockos_private.comandas (empresa_id, filial_id, id),
    unique (empresa_id, idempotency_key)
);
create index ix_stockos_pagamentos_comanda
  on stockos_private.pagamentos (empresa_id, filial_id, comanda_id);

create table stockos_private.movimentos_estoque (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    produto_id uuid not null,
    comanda_item_id uuid not null,
    tipo varchar(16) not null check (tipo in ('ENTREGA', 'ESTORNO')),
    quantidade integer not null check (quantidade > 0),
    registrado_por uuid not null,
    registrado_em timestamptz not null default now(),
    foreign key (empresa_id, filial_id, produto_id)
      references stockos_private.produtos (empresa_id, filial_id, id),
    foreign key (empresa_id, filial_id, comanda_item_id)
      references stockos_private.comanda_itens (empresa_id, filial_id, id),
    unique (comanda_item_id, tipo)
);

-- Defense in depth: API roles lack schema USAGE; RLS is enabled for private data too.
-- PostgreSQL owner/privileged backend connections may bypass RLS: application checks remain mandatory.
alter table stockos_private.empresas enable row level security;
alter table stockos_private.filiais enable row level security;
alter table stockos_private.membros enable row level security;
alter table stockos_private.produtos enable row level security;
alter table stockos_private.comandas enable row level security;
alter table stockos_private.comanda_itens enable row level security;
alter table stockos_private.pagamentos enable row level security;
alter table stockos_private.movimentos_estoque enable row level security;
