-- Tenant stock movement conservation for sale consumptions.
--
-- A stock exit (SAIDA_VENDA) may be split across multiple consumptions, but
-- the total linked amount must never exceed its original quantity.
-- No legacy rows are migrated/assigned. Tenant financial endpoints stay DENIED.
--
-- The movement FOR UPDATE lock serializes concurrent consumers of the same
-- stock exit; the aggregate is rechecked after each lock acquisition.

-- A tenant-owned consumption must always be tied to a real stock exit.
-- NOT VALID avoids rewriting/reclassifying pre-existing records, while still
-- enforcing the condition on new/updated rows. Review historical tenant data
-- before a later VALIDATE CONSTRAINT.
alter table venda_item_consumos
    add constraint ck_venda_consumos_tenant_movimento_requerido
    check (estabelecimento_id is null or movimento_estoque_id is not null) not valid;

create function stockos_guard_tenant_consumption_budget()
returns trigger language plpgsql as $$
declare
    source_tenant uuid;
    source_product integer;
    source_type varchar;
    source_quantity numeric(19,6);
    allocated_quantity numeric(19,6);
begin
    if new.estabelecimento_id is null then
        return new;
    end if;

    if new.movimento_estoque_id is null then
        raise exception 'Consumo do estabelecimento requer movimento de estoque.'
            using errcode = '23514';
    end if;

    select m.estabelecimento_id, m.produto_id, m.tipo, m.quantidade_base
      into source_tenant, source_product, source_type, source_quantity
      from movimentos_estoque_decimal m
     where m.id = new.movimento_estoque_id
     for update;

    if source_tenant is distinct from new.estabelecimento_id
       or source_product is distinct from new.produto_id
       or source_type is distinct from 'SAIDA_VENDA' then
        raise exception 'Movimento de consumo invalido para este estabelecimento.'
            using errcode = '23514';
    end if;

    select coalesce(sum(c.quantidade_base_total), 0)
      into allocated_quantity
      from venda_item_consumos c
     where c.movimento_estoque_id = new.movimento_estoque_id
       and (tg_op <> 'UPDATE' or c.id <> new.id);

    if allocated_quantity + new.quantidade_base_total > source_quantity then
        raise exception 'Consumos vinculados excedem a quantidade do movimento.'
            using errcode = '23514';
    end if;

    return new;
end;
$$;

create trigger trg_guard_tenant_consumption_budget
before insert or update of estabelecimento_id, venda_item_id, produto_id,
                           quantidade_base_total, movimento_estoque_id
on venda_item_consumos
for each row execute function stockos_guard_tenant_consumption_budget();

-- A referenced source/refund movement is an accounting fact. Editing any
-- of these fields could silently invalidate already accepted consumption/
-- refund links without firing triggers on their child records.
create function stockos_guard_linked_movement_immutable()
returns trigger language plpgsql as $$
begin
    if old.estabelecimento_id is not null
       and (
           new.produto_id is distinct from old.produto_id
           or new.tipo is distinct from old.tipo
           or new.movimento_estornado_id is distinct from old.movimento_estornado_id
           or new.quantidade_base is distinct from old.quantidade_base
       )
       and (
           exists (
               select 1 from venda_item_consumos c
                where c.movimento_estoque_id = old.id
           )
           or exists (
               select 1 from venda_item_consumo_estornos e
                where e.movimento_estoque_id = old.id
           )
       ) then
        raise exception 'Movimento utilizado em consumo/estorno nao pode ser reescrito.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_linked_movement_immutable
before update of produto_id, tipo, movimento_estornado_id, quantidade_base
on movimentos_estoque_decimal
for each row execute function stockos_guard_linked_movement_immutable();

create index idx_venda_consumos_tenant_movimento_accounting
    on venda_item_consumos (estabelecimento_id, movimento_estoque_id)
    where estabelecimento_id is not null and movimento_estoque_id is not null;

-- Still missing: concurrent two-transaction regression, tenant-scoped service
-- writes, payment reconciliation, balance/ledger reconciliation and auth E2E.
