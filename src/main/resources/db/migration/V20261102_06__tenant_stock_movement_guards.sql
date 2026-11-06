-- Tenant-aware stock records, additive and backwards-compatible.
-- Existing rows are deliberately left unassigned: NULL tenant != permission.
-- Do NOT expose the legacy global stock endpoints in SaaS mode.
-- filial_id is legacy metadata, NOT an authentication or tenant boundary.

alter table estoques_produto
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table movimentos_estoque_decimal
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table movimentos_estoque_decimal
    add constraint uk_movimentos_estoque_tenant_id unique (estabelecimento_id, id);

alter table estoques_produto
    add constraint fk_estoques_produto_tenant_produto
        foreign key (estabelecimento_id, produto_id)
        references produtos(estabelecimento_id, id) on update restrict;

alter table movimentos_estoque_decimal
    add constraint fk_movimentos_estoque_tenant_produto
        foreign key (estabelecimento_id, produto_id)
        references produtos(estabelecimento_id, id) on update restrict;

alter table movimentos_estoque_decimal
    add constraint fk_movimentos_estoque_tenant_estorno
        foreign key (estabelecimento_id, movimento_estornado_id)
        references movimentos_estoque_decimal(estabelecimento_id, id) on update restrict;

create index idx_estoques_produto_tenant
    on estoques_produto (estabelecimento_id, produto_id)
    where estabelecimento_id is not null;

create index idx_movimentos_estoque_tenant_criado
    on movimentos_estoque_decimal (estabelecimento_id, criado_em desc)
    where estabelecimento_id is not null;

-- PostgreSQL MATCH SIMPLE accepts nullable tenant keys; close that bypass
-- by requiring an exact match including NULL for legacy-to-legacy records.
create function stockos_guard_estoque_produto_tenant()
returns trigger language plpgsql as $$
declare
    product_tenant uuid;
begin
    select p.estabelecimento_id into product_tenant
      from produtos p where p.id = new.produto_id for key share;

    if new.estabelecimento_id is distinct from product_tenant then
        raise exception 'Produto de estoque pertence a outro estabelecimento.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_estoque_produto_tenant
before insert or update of estabelecimento_id, produto_id
on estoques_produto
for each row execute function stockos_guard_estoque_produto_tenant();

create function stockos_guard_movimento_estoque_tenant()
returns trigger language plpgsql as $$
declare
    product_tenant uuid;
    reversed_tenant uuid;
begin
    select p.estabelecimento_id into product_tenant
      from produtos p where p.id = new.produto_id for key share;

    if new.estabelecimento_id is distinct from product_tenant then
        raise exception 'Movimento de estoque com produto de outro estabelecimento.'
            using errcode = '23514';
    end if;

    if new.movimento_estornado_id is not null then
        select m.estabelecimento_id into reversed_tenant
          from movimentos_estoque_decimal m
         where m.id = new.movimento_estornado_id for key share;
        if new.estabelecimento_id is distinct from reversed_tenant then
            raise exception 'Estorno de estoque entre estabelecimentos nao permitido.'
                using errcode = '23514';
        end if;
    end if;
    return new;
end;
$$;

create trigger trg_guard_movimento_estoque_tenant
before insert or update of estabelecimento_id, produto_id, movimento_estornado_id
on movimentos_estoque_decimal
for each row execute function stockos_guard_movimento_estoque_tenant();

-- Prevent ordinary SQL updates from moving an already persisted row into
-- another tenant or silently appropriating data belonging to legacy users.
create trigger trg_estoques_produto_owner_immutable
before update of estabelecimento_id on estoques_produto
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_movimentos_estoque_owner_immutable
before update of estabelecimento_id on movimentos_estoque_decimal
for each row execute function stockos_catalog_owner_immutable();
