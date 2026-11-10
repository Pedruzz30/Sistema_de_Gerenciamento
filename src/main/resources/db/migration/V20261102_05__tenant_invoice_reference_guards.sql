-- Tenant-safe invoices and invoice line items. Additive, no legacy backfill.
-- All pre-existing invoice rows remain unassigned (estabelecimento_id NULL).
-- New tenant-owned invoices must reference a supplier in the SAME tenant;
-- their items must reference both an invoice and product from that tenant.
-- These constraints complement, but do not replace, authenticated SQL filtering.

alter table notas_fiscais
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table nota_fiscal_itens
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table notas_fiscais
    add constraint uk_notas_fiscais_tenant_id unique (estabelecimento_id, id);

alter table notas_fiscais
    add constraint fk_notas_tenant_fornecedor
        foreign key (estabelecimento_id, fornecedor_id)
        references fornecedores(estabelecimento_id, id) on update restrict;

alter table nota_fiscal_itens
    add constraint fk_nota_itens_tenant_nota
        foreign key (estabelecimento_id, nota_fiscal_id)
        references notas_fiscais(estabelecimento_id, id) on update restrict;

alter table nota_fiscal_itens
    add constraint fk_nota_itens_tenant_produto
        foreign key (estabelecimento_id, produto_id)
        references produtos(estabelecimento_id, id) on update restrict;

create index idx_notas_fiscais_tenant_id
    on notas_fiscais(estabelecimento_id, id)
    where estabelecimento_id is not null;

create index idx_nota_fiscal_itens_tenant_nota
    on nota_fiscal_itens(estabelecimento_id, nota_fiscal_id)
    where estabelecimento_id is not null;

-- MATCH SIMPLE on composite foreign keys would otherwise accept NULL tenant.
create function stockos_guard_notas_fiscais_tenant()
returns trigger language plpgsql as $$
declare
    supplier_tenant uuid;
begin
    select f.estabelecimento_id into supplier_tenant
      from fornecedores f where f.id = new.fornecedor_id for key share;

    if new.estabelecimento_id is distinct from supplier_tenant then
        raise exception 'Fornecedor nao pertence ao estabelecimento da nota.'
            using errcode = '23514';
    end if;
    -- New tenant-owned invoices must have a known employee membership.
    if new.estabelecimento_id is not null and not exists (
        select 1 from usuarios_estabelecimentos ue
         where ue.estabelecimento_id = new.estabelecimento_id
           and ue.usuario_ru = new.usuario_responsavel_ru
    ) then
        raise exception 'Operador da nota nao vinculado ao estabelecimento.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_notas_fiscais_tenant
before insert or update of estabelecimento_id, fornecedor_id, usuario_responsavel_ru
on notas_fiscais
for each row execute function stockos_guard_notas_fiscais_tenant();

create function stockos_guard_nota_fiscal_itens_tenant()
returns trigger language plpgsql as $$
declare
    invoice_tenant uuid;
    product_tenant uuid;
begin
    select n.estabelecimento_id into invoice_tenant
      from notas_fiscais n where n.id = new.nota_fiscal_id for key share;
    select p.estabelecimento_id into product_tenant
      from produtos p where p.id = new.produto_id for key share;

    if new.estabelecimento_id is distinct from invoice_tenant
       or new.estabelecimento_id is distinct from product_tenant then
        raise exception 'Nota fiscal e produto devem pertencer ao mesmo estabelecimento.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_nota_fiscal_itens_tenant
before insert or update of estabelecimento_id, nota_fiscal_id, produto_id
on nota_fiscal_itens
for each row execute function stockos_guard_nota_fiscal_itens_tenant();

-- Ordinary application SQL may never transfer an invoice across tenants.
create trigger trg_notas_fiscais_owner_immutable
before update of estabelecimento_id on notas_fiscais
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_nota_fiscal_itens_owner_immutable
before update of estabelecimento_id on nota_fiscal_itens
for each row execute function stockos_catalog_owner_immutable();

-- Legacy /api/notas still uses unscoped repositories and MUST remain blocked
-- in Supabase mode until its controller and service are migrated.
