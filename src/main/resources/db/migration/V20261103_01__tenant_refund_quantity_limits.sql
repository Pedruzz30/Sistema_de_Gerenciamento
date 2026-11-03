-- Tenant refund accounting: prevent cumulative stock returns from
-- exceeding either the recorded consumption OR the refund stock movement.
--
-- Only tenant-owned records are subject to this new control. Pre-existing
-- legacy records (estabelecimento_id IS NULL) are neither assigned nor rewritten.
-- APIs for checkout, cancel, refunds and cash remain DENIED in Supabase mode.
--
-- Lock the consumption row first and then the refund movement row. Competing
-- transactions touching the same records serialize before SUM is evaluated.
-- These checks do not replace authenticated tenant authorization, audit,
-- payment reconciliation or end-to-end tests.

create function stockos_guard_refund_quantity_limit()
returns trigger language plpgsql as $$
declare
    consumo_tenant uuid;
    consumo_quantidade numeric(19,6);
    movimento_tenant uuid;
    movimento_quantidade numeric(19,6);
    consumo_ja_estornado numeric(19,6);
    movimento_ja_distribuido numeric(19,6);
begin
    select c.estabelecimento_id, c.quantidade_base_total
      into consumo_tenant, consumo_quantidade
      from venda_item_consumos c
     where c.id = new.venda_item_consumo_id
     for update;

    -- The FK rejects an absent consumption. Legacy data is unchanged.
    if consumo_tenant is null then
        return new;
    end if;

    select m.estabelecimento_id, m.quantidade_base
      into movimento_tenant, movimento_quantidade
      from movimentos_estoque_decimal m
     where m.id = new.movimento_estoque_id
     for update;

    if new.estabelecimento_id is distinct from consumo_tenant
       or new.estabelecimento_id is distinct from movimento_tenant then
        raise exception 'Estorno fora do estabelecimento nao permitido.'
            using errcode = '23514';
    end if;

    select coalesce(sum(e.quantidade_base_estornada), 0)
      into consumo_ja_estornado
      from venda_item_consumo_estornos e
     where e.venda_item_consumo_id = new.venda_item_consumo_id
       and (tg_op <> 'UPDATE' or e.id <> new.id);

    if consumo_ja_estornado + new.quantidade_base_estornada > consumo_quantidade then
        raise exception 'Quantidade estornada excede o consumo registrado.'
            using errcode = '23514';
    end if;

    select coalesce(sum(e.quantidade_base_estornada), 0)
      into movimento_ja_distribuido
      from venda_item_consumo_estornos e
     where e.movimento_estoque_id = new.movimento_estoque_id
       and (tg_op <> 'UPDATE' or e.id <> new.id);

    if movimento_ja_distribuido + new.quantidade_base_estornada > movimento_quantidade then
        raise exception 'Quantidade vinculada excede o movimento de estorno.'
            using errcode = '23514';
    end if;

    return new;
end;
$$;

-- The V20261102_09 reference validator also runs on these association changes.
create trigger trg_stockos_refund_quantity_limit
before insert or update of quantidade_base_estornada, venda_item_consumo_id,
                           movimento_estoque_id, estabelecimento_id
on venda_item_consumo_estornos
for each row execute function stockos_guard_refund_quantity_limit();

-- Prevent editing the recorded consumption quantity after any tenant refund.
-- Without this guard, shrinking a consumed quantity could invalidate historic
-- refund sums, even if new refund inserts remain protected.
create function stockos_guard_consumption_quantity_after_refund()
returns trigger language plpgsql as $$
begin
    if old.estabelecimento_id is not null
       and new.quantidade_base_total is distinct from old.quantidade_base_total
       and exists (
           select 1 from venda_item_consumo_estornos e
            where e.venda_item_consumo_id = old.id
       ) then
        raise exception 'Consumo com estorno nao permite alterar a quantidade.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_stockos_consumption_quantity_after_refund
before update of quantidade_base_total on venda_item_consumos
for each row execute function stockos_guard_consumption_quantity_after_refund();

-- Once linked to a tenant refund, its stock movement's quantity must remain
-- stable; otherwise a prior, valid refund distribution could become excessive.
create function stockos_guard_refund_movement_quantity_immutable()
returns trigger language plpgsql as $$
begin
    if old.estabelecimento_id is not null
       and new.quantidade_base is distinct from old.quantidade_base
       and exists (
           select 1 from venda_item_consumo_estornos e
            where e.movimento_estoque_id = old.id
       ) then
        raise exception 'Movimento estornado nao permite alterar a quantidade.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_stockos_refund_movement_quantity_immutable
before update of quantidade_base on movimentos_estoque_decimal
for each row execute function stockos_guard_refund_movement_quantity_immutable();
