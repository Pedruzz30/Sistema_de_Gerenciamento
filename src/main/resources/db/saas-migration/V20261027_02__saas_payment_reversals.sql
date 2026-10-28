-- Full payment reversals are append-only; NEVER remove or rewrite original receipts.
-- Limited to one full reversal per payment. Split/partial reversal requires a future design.
alter table stockos_private.pagamentos
    add constraint uk_stockos_pagamento_parent_scope
    unique (empresa_id,filial_id,comanda_id,id);

create table stockos_private.pagamento_estornos (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    comanda_id uuid not null,
    pagamento_id uuid not null,
    caixa_turno_id uuid not null,
    modalidade varchar(16) not null
      check (modalidade in ('DINHEIRO','PIX','DEBITO','CREDITO')),
    valor numeric(19,2) not null check (valor>0),
    motivo varchar(240) not null check (length(trim(motivo))>=8),
    idempotency_key varchar(120) not null,
    registrado_por uuid not null,
    registrado_em timestamptz not null default now(),
    foreign key (empresa_id,filial_id,comanda_id,pagamento_id)
      references stockos_private.pagamentos(empresa_id,filial_id,comanda_id,id),
    foreign key (empresa_id,filial_id,caixa_turno_id)
      references stockos_private.caixa_turnos(empresa_id,filial_id,id),
    unique (pagamento_id),
    unique (empresa_id,idempotency_key)
);
create index ix_stockos_pagamento_estornos_filial
    on stockos_private.pagamento_estornos(empresa_id,filial_id,caixa_turno_id);

alter table stockos_private.pagamento_estornos enable row level security;
