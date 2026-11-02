-- Deny cross-tenant menu/category/product relationships in new writes.
-- Existing legacy rows without tenant remain allowed only with legacy parents.
alter table categorias_cardapio
    add constraint uk_categorias_cardapio_tenant_id unique (estabelecimento_id, id);

alter table itens_cardapio
    add constraint uk_itens_cardapio_tenant_id unique (estabelecimento_id, id);

alter table itens_cardapio
    add constraint fk_itens_cardapio_tenant_categoria
        foreign key (estabelecimento_id, categoria_cardapio_id)
        references categorias_cardapio(estabelecimento_id, id) on update restrict;

alter table itens_cardapio
    add constraint fk_itens_cardapio_tenant_produto
        foreign key (estabelecimento_id, produto_vinculado_id)
        references produtos(estabelecimento_id, id) on update restrict;

create function stockos_guard_item_cardapio_tenant()
returns trigger language plpgsql as $$
declare
    category_tenant uuid;
    product_tenant uuid;
begin
    select estabelecimento_id into category_tenant
      from categorias_cardapio where id = new.categoria_cardapio_id for key share;
    if new.estabelecimento_id is distinct from category_tenant then
        raise exception 'Categoria pertence a outro estabelecimento.'
            using errcode = '23514';
    end if;

    if new.produto_vinculado_id is not null then
        select estabelecimento_id into product_tenant
          from produtos where id = new.produto_vinculado_id for key share;
        if new.estabelecimento_id is distinct from product_tenant then
            raise exception 'Produto pertence a outro estabelecimento.'
                using errcode = '23514';
        end if;
    end if;
    return new;
end;
$$;

create trigger trg_guard_item_cardapio_tenant
before insert or update of estabelecimento_id, categoria_cardapio_id, produto_vinculado_id
on itens_cardapio
for each row execute function stockos_guard_item_cardapio_tenant();

-- Stronger association scoping for new tenant-aware paths still requires
-- explicit SQL WHERE tenant filters and a non-privileged JDBC user.
