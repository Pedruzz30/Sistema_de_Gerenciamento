-- StockOS multi-tenant foundation (schema only; NOT tenant isolation yet).
-- Additive migration: does not reassign legacy users, inventory, sales or money.
-- A tenant without an explicit active membership grants NO access.
create table estabelecimentos (
    id uuid primary key default gen_random_uuid(),
    nome varchar(160) not null,
    situacao varchar(16) not null default 'ATIVO',
    criado_em timestamptz not null default now(),
    constraint ck_estabelecimentos_nome check (length(btrim(nome)) >= 2),
    constraint ck_estabelecimentos_situacao check (situacao in ('ATIVO', 'SUSPENSO'))
);

create table usuarios_estabelecimentos (
    estabelecimento_id uuid not null references estabelecimentos(id) on delete restrict,
    usuario_ru bigint not null references usuarios(ru) on delete restrict,
    -- Local, non-secret employee identifier; may repeat across tenants.
    ru_local integer not null,
    papel varchar(20) not null,
    ativo boolean not null default true,
    criado_em timestamptz not null default now(),
    constraint pk_usuarios_estabelecimentos primary key (estabelecimento_id, usuario_ru),
    constraint uk_usuarios_estabelecimentos_ru_local unique (estabelecimento_id, ru_local),
    constraint ck_usuarios_estabelecimentos_ru_local check (ru_local > 0),
    constraint ck_usuarios_estabelecimentos_papel check (papel in ('PROPRIETARIO', 'GERENTE', 'OPERADOR'))
);

create index idx_usuarios_estabelecimentos_usuario
    on usuarios_estabelecimentos (usuario_ru, estabelecimento_id)
    where ativo = true;

-- Deliberately NO default tenant, implicit owner, or legacy-data backfill.
-- The current legacy APIs still use global tables; do not expose this as SaaS.
