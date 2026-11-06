-- Tenant ownership of sale headers and sale lines.
-- Additive migration: legacy sales remain NULL tenant and are not reclassified.
-- This is not a complete SaaS conversion of sales: payments, consumption,
-- technical recipes and refunds still require separate reviewed migrations.

alter table vendas
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table venda_itens
    add column estabelecimento_id uuid references estabelecimentos(id) on delete restrict;

alter table vendas
    add constraint uk_vendas_tenant_id unique (estabelecimento_id, id);

alter table venda_itens
    add constraint uk_venda_itens_tenant_id unique (estabelecimento_id, id);

alter table venda_itens
    add constraint fk_venda_itens_tenant_venda
        foreign key (estabelecimento_id, venda_id)
        references vendas(estabelecimento_id, id) on update restrict;

alter table venda_itens
    add constraint fk_venda_itens_tenant_item_cardapio
        foreign key (estabelecimento_id, item_cardapio_id)
        references itens_cardapio(estabelecimento_id, id) on update restrict;

create index idx_vendas_tenant_criada
    on vendas (estabelecimento_id, criada_em desc, id desc)
    where estabelecimento_id is not null;

create index idx_venda_itens_tenant_venda
    on venda_itens (estabelecimento_id, venda_id)
    where estabelecimento_id is not null;

-- Revalidate on inserts and relationship mutations, even if the client omits
-- estabelecimento_id to bypass a nullable composite FK (MATCH SIMPLE).
create function stockos_guard_venda_item_tenant()
returns trigger language plpgsql as $$
declare
    sale_tenant uuid;
    menu_tenant uuid;
    variation_item_id integer;
begin
    select v.estabelecimento_id into sale_tenant
      from vendas v where v.id = new.venda_id for key share;
    select i.estabelecimento_id into menu_tenant
      from itens_cardapio i where i.id = new.item_cardapio_id for key share;
    select var.item_cardapio_id into variation_item_id
      from variacoes_item_cardapio var
     where var.id = new.variacao_item_cardapio_id for key share;

    if new.estabelecimento_id is distinct from sale_tenant
       or new.estabelecimento_id is distinct from menu_tenant
       or variation_item_id is distinct from new.item_cardapio_id then
        raise exception 'Item de venda nao pertence ao estabelecimento ou variacao.'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_guard_venda_item_tenant
before insert or update of estabelecimento_id, venda_id,
                           item_cardapio_id, variacao_item_cardapio_id
on venda_itens
for each row execute function stockos_guard_venda_item_tenant();

create trigger trg_vendas_owner_immutable
before update of estabelecimento_id on vendas
for each row execute function stockos_catalog_owner_immutable();

create trigger trg_venda_itens_owner_immutable
before update of estabelecimento_id on venda_itens
for each row execute function stockos_catalog_owner_immutable();

-- Future migrations must scope item consumption, estornos, receitas, stock
-- movements and cash settlements before sales endpoints may be opened.
