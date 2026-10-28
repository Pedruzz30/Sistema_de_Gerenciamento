-- Inbound goods for StockOS SaaS. Not an invoice authorization or accounts-payable module.
-- Company, branch and product FKs prevent cross-establishment receipt rows.
create table stockos_private.fornecedores (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null references stockos_private.empresas(id),
    nome varchar(160) not null check (length(trim(nome)) >= 2),
    documento varchar(32),
    ativo boolean not null default true,
    criado_por uuid not null,
    criado_em timestamptz not null default now(),
    unique (empresa_id,id),
    check (documento is null or length(trim(documento)) >= 5)
);
create unique index uk_stockos_fornecedor_documento
    on stockos_private.fornecedores(empresa_id,documento)
    where documento is not null;

create table stockos_private.entradas_mercadoria (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    fornecedor_id uuid not null,
    documento_referencia varchar(80) not null check(length(trim(documento_referencia)) >= 3),
    idempotency_key varchar(120) not null,
    payload_hash char(64) not null,
    custo_total numeric(19,2) not null check (custo_total >= 0),
    registrado_por uuid not null,
    registrado_em timestamptz not null default now(),
    foreign key (empresa_id,filial_id)
       references stockos_private.filiais(empresa_id,id),
    foreign key (empresa_id,fornecedor_id)
       references stockos_private.fornecedores(empresa_id,id),
    unique (empresa_id,filial_id,fornecedor_id,documento_referencia),
    unique (empresa_id,idempotency_key),
    unique (empresa_id,filial_id,id)
);
create index ix_stockos_entradas_filial
    on stockos_private.entradas_mercadoria (empresa_id,filial_id,registrado_em desc);

create table stockos_private.entrada_mercadoria_itens (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    entrada_id uuid not null,
    produto_id uuid not null,
    quantidade integer not null check (quantidade > 0),
    custo_unitario numeric(19,2) not null check (custo_unitario >= 0),
    nome_snapshot varchar(160) not null,
    foreign key (empresa_id,filial_id,entrada_id)
       references stockos_private.entradas_mercadoria(empresa_id,filial_id,id),
    foreign key (empresa_id,filial_id,produto_id)
       references stockos_private.produtos(empresa_id,filial_id,id),
    unique (entrada_id,produto_id)
);
create index ix_stockos_entrada_itens_produto
    on stockos_private.entrada_mercadoria_itens(empresa_id,filial_id,produto_id);

alter table stockos_private.fornecedores enable row level security;
alter table stockos_private.entradas_mercadoria enable row level security;
alter table stockos_private.entrada_mercadoria_itens enable row level security;
