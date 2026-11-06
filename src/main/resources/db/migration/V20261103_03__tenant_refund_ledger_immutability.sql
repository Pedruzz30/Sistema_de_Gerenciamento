-- Preserve tenant-owned stock-refund ledger after first posting.
-- Existing legacy rows are untouched. No payment/refund HTTP routes enabled.
--
-- Once a consumption has refund entries, changing its parent sale line,
-- product, source movement or composition part could leave a previously
-- accepted stock return pointing at the wrong origin.
create function stockos_guard_refunded_consumption_references()
returns trigger language plpgsql as $$
begin
    if old.estabelecimento_id is not null
       and (
           new.venda_item_id is distinct from old.venda_item_id
           or new.venda_item_parte_id is distinct from old.venda_item_parte_id
           or new.produto_id is distinct from old.produto_id
           or new.movimento_estoque_id is distinct from old.movimento_estoque_id
       )
       and exists (
           select 1 from venda_item_consumo_estornos e
            where e.venda_item_consumo_id = old.id
       ) then
        raise exception 'Consumo com estorno nao permite trocar suas referencias.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_refunded_consumption_references
before update of venda_item_id, venda_item_parte_id, produto_id, movimento_estoque_id
on venda_item_consumos
for each row execute function stockos_guard_refunded_consumption_references();

-- Refund rows are an immutable stock ledger; adjustments must be separate
-- compensating events, not edits to already posted quantity/amount/link.
create function stockos_guard_posted_refund_immutable()
returns trigger language plpgsql as $$
begin
    if old.estabelecimento_id is not null then
        raise exception 'Estorno registrado nao pode ser alterado ou excluido.'
            using errcode = '23514';
    end if;
    return case when tg_op = 'DELETE' then old else new end;
end;
$$;

create trigger trg_guard_posted_refund_immutable
before update or delete on venda_item_consumo_estornos
for each row execute function stockos_guard_posted_refund_immutable();

-- Intentionally does not block original legacy-only rows. Historical tenant
-- data is not reclassified. HTTP flows remain default-deny.
