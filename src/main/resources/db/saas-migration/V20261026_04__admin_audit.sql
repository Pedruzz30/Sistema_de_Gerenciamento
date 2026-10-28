-- Append-only administrative audit trail (application enforces write-only audit operations).
create table stockos_private.admin_auditoria (
    id bigint generated always as identity primary key,
    ator_auth_user_id uuid not null,
    acao varchar(80) not null,
    empresa_id uuid references stockos_private.empresas(id),
    alvo_id uuid,
    detalhes varchar(1000) not null,
    criada_em timestamptz not null default now()
);
create index ix_stockos_admin_auditoria_data on stockos_private.admin_auditoria(criada_em desc,id desc);
alter table stockos_private.admin_auditoria enable row level security;
