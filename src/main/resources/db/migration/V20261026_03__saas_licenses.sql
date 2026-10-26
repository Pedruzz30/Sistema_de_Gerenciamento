-- StockOS subscription catalog. Manual provisioning ONLY in pilot environments.
-- No payment gateway or automated renewal is implied by the data model.
create table stockos_private.planos (
    id uuid primary key default gen_random_uuid(),
    codigo varchar(32) not null unique,
    nome varchar(100) not null,
    preco_mensal numeric(19,2) not null check (preco_mensal>=0),
    ativo boolean not null default true,
    inclui_comandas boolean not null default true,
    max_filiais integer not null check (max_filiais>0),
    max_usuarios integer not null check (max_usuarios>0)
);

create table stockos_private.licencas (
    empresa_id uuid primary key references stockos_private.empresas(id),
    plano_id uuid not null references stockos_private.planos(id),
    status varchar(16) not null
        check (status in ('TRIAL','ATIVA','SUSPENSA','CANCELADA')),
    vigencia_inicio timestamptz not null,
    vigencia_fim timestamptz not null,
    valor_mensal_snapshot numeric(19,2) not null check (valor_mensal_snapshot>=0),
    atualizada_em timestamptz not null default now(),
    check (vigencia_fim>vigencia_inicio)
);

create index ix_stockos_licencas_vencimento
    on stockos_private.licencas (vigencia_fim, status);

-- No policies for anon/authenticated; privileged server-only access via private schema.
alter table stockos_private.planos enable row level security;
alter table stockos_private.licencas enable row level security;
