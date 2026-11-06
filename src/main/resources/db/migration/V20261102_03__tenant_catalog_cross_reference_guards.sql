-- Tenant referential integrity for product/supplier associations.
-- This migration is additive and does NOT convert legacy rows to tenants.
-- Legacy association rows are allowed only if BOTH parents are legacy (NULL).
-- Once a catalog record is owned by a tenant its owner is immutable;
-- migrating legacy records requires a separate reviewed migration.

alter table produtos
    add constraint uk_produtos_tenant_id unique (estabelecimento_id, id);

alter table fornecedores
    add constraint uk_fornecedores_tenant_id unique (estabelecimento_id, id);

alter table fornecedor_produtos
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table fornecedor_produtos
    add constraint fk_fornecedor_produtos_tenant_fornecedor
        foreign key (estabelecimento_id, fornecedor_id)
        references fornecedores(estabelecimento_id, id) on update restrict;

alter table fornecedor_produtos
    add constraint fk_fornecedor_produtos_tenant_produto
        foreign key (estabelecimento_id, produto_id)
        references produtos(estabelecimento_id, id) on update restrict;

create index idx_fornecedor_produtos_tenant
    on fornecedor_produtos(estabelecimento_id, fornecedor_id, produto_id)
    where estabelecimento_id is not null;

-- Composite FK MATCH SIMPLE ignores NULL; this trigger closes that bypass.
-- Keep reading parent rows locked against ownership/key changes during validation.
create function stockos_guard_fornecedor_produtos_tenant()
returns trigger language plpgsql as $$
declare
    supplier_tenant uuid;
    product_tenant uuid;
begin
    select estabelecimento_id into supplier_tenant
      from fornecedores where id = new.fornecedor_id for key share;
    select estabelecimento_id into product_tenant
      from produtos where id = new.produto_id for key share;

    if supplier_tenant is distinct from product_tenant
       or new.estabelecimento_id is distinct from supplier_tenant then
        raise exception 'Associacao entre estabelecimentos diferentes nao permitida.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_fornecedor_produtos_tenant
before insert or update of estabelecimento_id, fornecedor_id, produto_id
on fornecedor_produtos
for each row execute function stockos_guard_fornecedor_produtos_tenant();

-- Tenant ownership of master records is immutable through normal DML,
-- including attempts to appropriate unassigned legacy records.
-- Do NOT disable these triggers without an audited migration with backup.
create function stockos_catalog_owner_immutable()
returns trigger language plpgsql as $$
begin
    if old.estabelecimento_id is distinct from new.estabelecimento_id then
        raise exception 'Propriedade de estabelecimento nao pode ser alterada.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_produtos_owner_immutable
before update of estabelecimento_id on produtos
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_fornecedores_owner_immutable
before update of estabelecimento_id on fornecedores
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_caixas_owner_immutable
before update of estabelecimento_id on caixas
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_categorias_cardapio_owner_immutable
before update of estabelecimento_id on categorias_cardapio
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_itens_cardapio_owner_immutable
before update of estabelecimento_id on itens_cardapio
for each row execute function stockos_catalog_owner_immutable();

-- This does NOT prevent legacy global queries from leaking data.
-- Tenant-aware HTTP endpoints must still be built and audited.
