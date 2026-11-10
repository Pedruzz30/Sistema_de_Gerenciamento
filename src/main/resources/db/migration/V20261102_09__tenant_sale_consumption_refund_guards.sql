-- Protect sale consumption and refunds against cross-tenant references.
-- Additive: legacy rows stay unassigned (NULL tenant); no ownership backfill.
-- NOT an authorization layer and NOT permission to enable sale/refund endpoints.
--
-- Nullable composite foreign keys use MATCH SIMPLE. Database triggers below
-- enforce exact tenant equality (including NULL) on every new association.

alter table venda_item_partes
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table venda_item_consumos
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table venda_item_consumo_estornos
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table venda_item_partes
    add constraint uk_venda_item_partes_tenant_id unique (estabelecimento_id, id);

alter table venda_item_consumos
    add constraint uk_venda_item_consumos_tenant_id unique (estabelecimento_id, id);

alter table venda_item_partes
    add constraint fk_venda_item_partes_tenant_item
    foreign key (estabelecimento_id, venda_item_id)
    references venda_itens (estabelecimento_id, id) on update restrict;

alter table venda_item_partes
    add constraint fk_venda_item_partes_tenant_cardapio
    foreign key (estabelecimento_id, item_cardapio_parte_id)
    references itens_cardapio (estabelecimento_id, id) on update restrict;

alter table venda_item_consumos
    add constraint fk_venda_consumos_tenant_item
    foreign key (estabelecimento_id, venda_item_id)
    references venda_itens (estabelecimento_id, id) on update restrict;

alter table venda_item_consumos
    add constraint fk_venda_consumos_tenant_produto
    foreign key (estabelecimento_id, produto_id)
    references produtos (estabelecimento_id, id) on update restrict;

alter table venda_item_consumos
    add constraint fk_venda_consumos_tenant_movimento
    foreign key (estabelecimento_id, movimento_estoque_id)
    references movimentos_estoque_decimal (estabelecimento_id, id) on update restrict;

alter table venda_item_consumos
    add constraint fk_venda_consumos_tenant_parte
    foreign key (estabelecimento_id, venda_item_parte_id)
    references venda_item_partes (estabelecimento_id, id) on update restrict;

alter table venda_item_consumo_estornos
    add constraint fk_venda_consumo_estornos_tenant_consumo
    foreign key (estabelecimento_id, venda_item_consumo_id)
    references venda_item_consumos (estabelecimento_id, id) on update restrict;

alter table venda_item_consumo_estornos
    add constraint fk_venda_consumo_estornos_tenant_movimento
    foreign key (estabelecimento_id, movimento_estoque_id)
    references movimentos_estoque_decimal (estabelecimento_id, id) on update restrict;

create index idx_venda_item_partes_tenant_item
    on venda_item_partes (estabelecimento_id, venda_item_id)
    where estabelecimento_id is not null;

create index idx_venda_consumos_tenant_item
    on venda_item_consumos (estabelecimento_id, venda_item_id)
    where estabelecimento_id is not null;

create index idx_venda_consumo_estornos_tenant_consumo
    on venda_item_consumo_estornos (estabelecimento_id, venda_item_consumo_id)
    where estabelecimento_id is not null;

-- Validate the parent sale item, menu item and variation together.
create function stockos_guard_venda_item_parte_tenant()
returns trigger language plpgsql as $$
declare
    item_tenant uuid;
    menu_tenant uuid;
    variation_item integer;
begin
    select vi.estabelecimento_id into item_tenant
      from venda_itens vi where vi.id = new.venda_item_id for key share;
    select ic.estabelecimento_id into menu_tenant
      from itens_cardapio ic where ic.id = new.item_cardapio_parte_id for key share;
    select v.item_cardapio_id into variation_item
      from variacoes_item_cardapio v where v.id = new.variacao_parte_id for key share;

    if new.estabelecimento_id is distinct from item_tenant
       or new.estabelecimento_id is distinct from menu_tenant
       or variation_item is distinct from new.item_cardapio_parte_id then
        raise exception 'Parte da venda referencia outro estabelecimento ou variacao.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_venda_item_parte_tenant
before insert or update of estabelecimento_id, venda_item_id,
                           variacao_parte_id, item_cardapio_parte_id
on venda_item_partes
for each row execute function stockos_guard_venda_item_parte_tenant();

-- Consumption must belong to the sale line, stock product and movement.
-- An optional composition part must belong to that same sale line.
create function stockos_guard_venda_consumo_tenant()
returns trigger language plpgsql as $$
declare
    item_tenant uuid;
    product_tenant uuid;
    move_tenant uuid;
    move_product integer;
    move_type varchar;
    part_tenant uuid;
    part_item bigint;
begin
    select vi.estabelecimento_id into item_tenant
      from venda_itens vi where vi.id = new.venda_item_id for key share;
    select p.estabelecimento_id into product_tenant
      from produtos p where p.id = new.produto_id for key share;

    if new.estabelecimento_id is distinct from item_tenant
       or new.estabelecimento_id is distinct from product_tenant then
        raise exception 'Consumo de venda pertence a outro estabelecimento.'
            using errcode = '23514';
    end if;

    if new.venda_item_parte_id is not null then
        select vp.estabelecimento_id, vp.venda_item_id
          into part_tenant, part_item
          from venda_item_partes vp where vp.id = new.venda_item_parte_id for key share;
        if new.estabelecimento_id is distinct from part_tenant
           or new.venda_item_id is distinct from part_item then
            raise exception 'Parte de consumo nao pertence ao item e estabelecimento.'
                using errcode = '23514';
        end if;
    end if;

    if new.movimento_estoque_id is not null then
        select m.estabelecimento_id, m.produto_id, m.tipo
          into move_tenant, move_product, move_type
          from movimentos_estoque_decimal m
         where m.id = new.movimento_estoque_id for key share;
        if new.estabelecimento_id is distinct from move_tenant
           or new.produto_id is distinct from move_product
           or (new.estabelecimento_id is not null and move_type is distinct from 'SAIDA_VENDA') then
            raise exception 'Movimento de consumo pertence a outro produto ou estabelecimento.'
                using errcode = '23514';
        end if;
    end if;
    return new;
end;
$$;

create trigger trg_guard_venda_consumo_tenant
before insert or update of estabelecimento_id, venda_item_id, produto_id,
                           movimento_estoque_id, venda_item_parte_id
on venda_item_consumos
for each row execute function stockos_guard_venda_consumo_tenant();

-- A tenant-owned refund must use a genuine ESTORNO_VENDA movement reversing
-- the EXACT stock movement originally consumed, not just the same tenant.
create function stockos_guard_venda_consumo_estorno_tenant()
returns trigger language plpgsql as $$
declare
    consumed_tenant uuid;
    original_move bigint;
    consumed_product integer;
    refund_tenant uuid;
    refund_product integer;
    refund_type varchar;
    reversed_move bigint;
begin
    select c.estabelecimento_id, c.movimento_estoque_id, c.produto_id
      into consumed_tenant, original_move, consumed_product
      from venda_item_consumos c
     where c.id = new.venda_item_consumo_id for key share;
    select m.estabelecimento_id, m.produto_id, m.tipo, m.movimento_estornado_id
      into refund_tenant, refund_product, refund_type, reversed_move
      from movimentos_estoque_decimal m
     where m.id = new.movimento_estoque_id for key share;

    if new.estabelecimento_id is distinct from consumed_tenant
       or new.estabelecimento_id is distinct from refund_tenant then
        raise exception 'Estorno de consumo entre estabelecimentos nao permitido.'
            using errcode = '23514';
    end if;

    if new.estabelecimento_id is not null
       and (original_move is null
            or reversed_move is distinct from original_move
            or refund_product is distinct from consumed_product
            or refund_type is distinct from 'ESTORNO_VENDA') then
        raise exception 'Estorno nao corresponde ao consumo ou movimento original.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_venda_consumo_estorno_tenant
before insert or update of estabelecimento_id, venda_item_consumo_id, movimento_estoque_id
on venda_item_consumo_estornos
for each row execute function stockos_guard_venda_consumo_estorno_tenant();

create trigger trg_venda_item_partes_owner_immutable
before update of estabelecimento_id on venda_item_partes
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_venda_consumos_owner_immutable
before update of estabelecimento_id on venda_item_consumos
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_venda_consumo_estornos_owner_immutable
before update of estabelecimento_id on venda_item_consumo_estornos
for each row execute function stockos_catalog_owner_immutable();

-- NOTE: This does NOT enforce refund totals or protect checkout transactions,
-- idempotency, compensation and old services. APIs remain DENIED in Supabase mode.
