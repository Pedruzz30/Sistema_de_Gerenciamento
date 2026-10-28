create table stockos_private.convites_funcionarios (
 id uuid primary key default gen_random_uuid(),
 empresa_id uuid not null references stockos_private.empresas(id),
 email_normalizado varchar(254) not null,
 papel varchar(24) not null check (papel in ('GERENTE','ATENDENTE')),
 token_sha256 char(64) not null unique,
 status varchar(12) not null default 'PENDENTE'
    check(status in ('PENDENTE','ACEITO','REVOGADO')),
 criado_por uuid not null,
 criado_em timestamptz not null default now(),
 expira_em timestamptz not null,
 aceito_por uuid,
 aceito_em timestamptz,
 revogado_por uuid,
 revogado_em timestamptz,
 check(expira_em > criado_em)
);
create unique index uk_stockos_convites_email_pending
 on stockos_private.convites_funcionarios(empresa_id,email_normalizado)
 where status='PENDENTE';
create index ix_stockos_convites_empresa
 on stockos_private.convites_funcionarios(empresa_id,criado_em desc);
alter table stockos_private.convites_funcionarios enable row level security;