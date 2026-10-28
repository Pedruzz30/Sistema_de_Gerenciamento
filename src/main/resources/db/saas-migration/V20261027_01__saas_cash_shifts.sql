-- Dedicated business-shift cash register for the StockOS SaaS.
-- Existing legacy cash register remains isolated; no automatic data migration.
create table stockos_private.caixa_turnos (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    status varchar(12) not null default 'ABERTO'
        check (status in ('ABERTO','FECHADO')),
    valor_abertura numeric(19,2) not null check (valor_abertura >= 0),
    aberto_por uuid not null,
    aberto_em timestamptz not null default now(),
    fechado_por uuid,
    fechado_em timestamptz,
    valor_esperado numeric(19,2),
    valor_contado numeric(19,2),
    diferenca numeric(19,2),
    foreign key (empresa_id,filial_id)
        references stockos_private.filiais(empresa_id,id),
    unique (empresa_id,filial_id,id),
    check (
      (status='ABERTO' and fechado_em is null and fechado_por is null
       and valor_esperado is null and valor_contado is null and diferenca is null)
      or
      (status='FECHADO' and fechado_em is not null and fechado_por is not null
       and valor_esperado is not null and valor_contado is not null and diferenca is not null)
    )
);
create unique index uk_stockos_caixa_aberto_por_filial
    on stockos_private.caixa_turnos(filial_id) where status='ABERTO';
create index ix_stockos_caixa_turnos_historico
    on stockos_private.caixa_turnos(empresa_id,filial_id,aberto_em desc);

-- Nullable for any historical test entries from staging. All new API payments
-- must set this reference; future backfill can enforce NOT NULL separately.
alter table stockos_private.pagamentos
    add column caixa_turno_id uuid;
alter table stockos_private.pagamentos
    add constraint fk_stockos_pagamentos_caixa_turno
    foreign key (empresa_id,filial_id,caixa_turno_id)
    references stockos_private.caixa_turnos(empresa_id,filial_id,id);
create index ix_stockos_pagamentos_turno
    on stockos_private.pagamentos (empresa_id,filial_id,caixa_turno_id);

create table stockos_private.caixa_movimentos (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    caixa_turno_id uuid not null,
    tipo varchar(16) not null check (tipo in ('SUPRIMENTO','SANGRIA')),
    valor numeric(19,2) not null check (valor>0),
    motivo varchar(240) not null check (length(trim(motivo))>=5),
    idempotency_key varchar(120) not null,
    registrado_por uuid not null,
    registrado_em timestamptz not null default now(),
    foreign key (empresa_id,filial_id,caixa_turno_id)
      references stockos_private.caixa_turnos (empresa_id,filial_id,id),
    unique (empresa_id,idempotency_key)
);
create index ix_stockos_caixa_movimentos_turno
    on stockos_private.caixa_movimentos(empresa_id,filial_id,caixa_turno_id);

alter table stockos_private.caixa_turnos enable row level security;
alter table stockos_private.caixa_movimentos enable row level security;
