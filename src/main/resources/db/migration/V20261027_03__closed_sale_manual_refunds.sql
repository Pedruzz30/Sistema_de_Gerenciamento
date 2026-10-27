-- Historic closed sales remain immutable. Refunds of closed sales are separate
-- manually-confirmed financial events, recorded in the CURRENT cash shift.
create table stockos_private.devolucoes_pos_fechamento (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    filial_id uuid not null,
    comanda_id uuid not null,
    pagamento_id uuid not null,
    turno_processamento_id uuid not null,
    modalidade varchar(16) not null
      check (modalidade in ('DINHEIRO','PIX','DEBITO','CREDITO')),
    valor numeric(19,2) not null check (valor > 0),
    motivo varchar(240) not null check (length(trim(motivo)) >= 8),
    comprovante_referencia varchar(160) not null
      check (length(trim(comprovante_referencia)) >= 6),
    -- The employee declares external settlement, it is NOT independently verified.
    declarado_confirmado boolean not null check (declarado_confirmado = true),
    idempotency_key varchar(120) not null,
    registrado_por uuid not null,
    registrado_em timestamptz not null default now(),
    foreign key (empresa_id,filial_id,comanda_id,pagamento_id)
      references stockos_private.pagamentos(empresa_id,filial_id,comanda_id,id),
    foreign key (empresa_id,filial_id,turno_processamento_id)
      references stockos_private.caixa_turnos(empresa_id,filial_id,id),
    unique (pagamento_id),
    unique (empresa_id,idempotency_key)
);
create index ix_stockos_devolucoes_turno
 on stockos_private.devolucoes_pos_fechamento
 (empresa_id,filial_id,turno_processamento_id);
alter table stockos_private.devolucoes_pos_fechamento enable row level security;
