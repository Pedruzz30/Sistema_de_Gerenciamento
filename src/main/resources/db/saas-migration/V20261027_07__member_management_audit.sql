create table stockos_private.membros_auditoria (
 id bigint generated always as identity primary key,
 empresa_id uuid not null references stockos_private.empresas(id),
 ator_auth_user_id uuid not null,
 alvo_auth_user_id uuid not null,
 papel_anterior varchar(24) not null,
 papel_novo varchar(24) not null,
 ativo_anterior boolean not null,
 ativo_novo boolean not null,
 registrado_em timestamptz not null default now()
);
create index ix_stockos_membros_auditoria on stockos_private.membros_auditoria (empresa_id,registrado_em desc);
alter table stockos_private.membros_auditoria enable row level security;