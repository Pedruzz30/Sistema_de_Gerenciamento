-- Additive tenant ownership for the first operational domains.
-- Legacy rows deliberately remain NULL: never guess their establishment.
-- Only tenant-scoped services may access owned rows in SaaS mode.
-- Unscoped legacy repositories/endpoints must be replaced before deployment.
alter table produtos
    add column estabelecimento_id uuid
    references estabelecimentos(id) on delete restrict;

alter table fornecedores
    add column estabelecimento_id uuid
    references estabelecimentos(id) on delete restrict;

alter table caixas
    add column estabelecimento_id uuid
    references estabelecimentos(id) on delete restrict;

alter table categorias_cardapio
    add column estabelecimento_id uuid
    references estabelecimentos(id) on delete restrict;

alter table itens_cardapio
    add column estabelecimento_id uuid
    references estabelecimentos(id) on delete restrict;

create index idx_produtos_estabelecimento_id
    on produtos(estabelecimento_id, id)
    where estabelecimento_id is not null;

create index idx_fornecedores_estabelecimento_id
    on fornecedores(estabelecimento_id, id)
    where estabelecimento_id is not null;

create index idx_caixas_estabelecimento_id
    on caixas(estabelecimento_id, id)
    where estabelecimento_id is not null;

create index idx_categorias_cardapio_estabelecimento_id
    on categorias_cardapio(estabelecimento_id, id)
    where estabelecimento_id is not null;

create index idx_itens_cardapio_estabelecimento_id
    on itens_cardapio(estabelecimento_id, id)
    where estabelecimento_id is not null;

-- NOTE: No NOT NULL, backfill, scoped unique constraints or FK rewrites yet.
-- Legacy foreign keys and writes are NOT tenant-safe. A separate migration
-- will enforce cross-table ownership after all dependent flows are ported.
