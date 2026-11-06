-- Tenant-safe cash-register and cash-movement relationships.
-- Additive schema: legacy records retain NULL estabelecimento_id.
-- DO NOT enable legacy cash/finance HTTP controllers in SaaS mode.
--
-- A cash movement must match its register's tenant, and a tenant-scoped
-- movement's operator must be an active member of the same establishment.

alter table movimentacoes_caixa
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table caixas
    add constraint uk_caixas_tenant_id unique (estabelecimento_id, id);

alter table movimentacoes_caixa
    add constraint fk_movimentacoes_caixa_tenant_caixa
        foreign key (estabelecimento_id, caixa_id)
        references caixas(estabelecimento_id, id) on update restrict;

create index idx_movimentacoes_caixa_tenant_caixa
    on movimentacoes_caixa(estabelecimento_id, caixa_id, id)
    where estabelecimento_id is not null;

create index idx_caixas_tenant_status
    on caixas(estabelecimento_id, status, id)
    where estabelecimento_id is not null;

-- Validate current assigned cash operator on new and changed cash registers.
-- Historic legacy registers are not reclassified or updated automatically.
create function stockos_guard_caixa_operador_tenant()
returns trigger language plpgsql as $$
begin
    if new.estabelecimento_id is not null
       and new.operador_atual_ru is not null
       and not exists (
           select 1 from usuarios_estabelecimentos ue
           join usuarios u on u.ru = ue.usuario_ru
           join estabelecimentos e on e.id = ue.estabelecimento_id
           where ue.estabelecimento_id = new.estabelecimento_id
             and ue.usuario_ru = new.operador_atual_ru
             and ue.ativo = true and u.ativo = true and e.situacao = 'ATIVO'
       ) then
        raise exception 'Operador de caixa sem acesso ao estabelecimento.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_caixa_operador_tenant
before insert or update of operador_atual_ru, estabelecimento_id
on caixas for each row execute function stockos_guard_caixa_operador_tenant();

create function stockos_guard_movimento_caixa_tenant()
returns trigger language plpgsql as $$
declare
    caixa_tenant uuid;
begin
    select c.estabelecimento_id into caixa_tenant
      from caixas c where c.id = new.caixa_id for key share;

    -- MATCH SIMPLE permits NULL: the IS DISTINCT FROM comparison closes it.
    if new.estabelecimento_id is distinct from caixa_tenant then
        raise exception 'Movimentacao de caixa de outro estabelecimento.'
            using errcode = '23514';
    end if;

    if new.estabelecimento_id is not null
       and not exists (
           select 1 from usuarios_estabelecimentos ue
           join usuarios u on u.ru = ue.usuario_ru
           join estabelecimentos e on e.id = ue.estabelecimento_id
           where ue.estabelecimento_id = new.estabelecimento_id
             and ue.usuario_ru = new.operador_ru
             and ue.ativo = true and u.ativo = true and e.situacao = 'ATIVO'
       ) then
        raise exception 'Operador de movimento sem acesso ao estabelecimento.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_movimento_caixa_tenant
before insert or update of estabelecimento_id, caixa_id, operador_ru
on movimentacoes_caixa for each row execute function stockos_guard_movimento_caixa_tenant();

create trigger trg_movimentacoes_caixa_owner_immutable
before update of estabelecimento_id on movimentacoes_caixa
for each row execute function stockos_catalog_owner_immutable();

-- Cash movement authorizations, payments and financial reconciliation need
-- service-level transactions and tests; these triggers alone are insufficient.
