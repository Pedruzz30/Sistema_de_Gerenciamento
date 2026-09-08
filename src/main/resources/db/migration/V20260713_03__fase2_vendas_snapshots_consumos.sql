-- Migration de referencia da Fase 2.
-- O projeto ainda usa spring.jpa.hibernate.ddl-auto=update; este arquivo documenta
-- o schema esperado para ativacao futura de Flyway/baseline, sem ser aplicado automaticamente.

alter table variacoes_item_cardapio
    add column if not exists quantidade_produto_direto_base numeric(19, 6);

create table if not exists vendas (
    id bigserial primary key,
    filial_id bigint not null,
    numero varchar(40) not null,
    status varchar(30) not null,
    canal varchar(40) not null,
    subtotal numeric(19, 4) not null,
    desconto_total numeric(19, 4) not null,
    acrescimo_total numeric(19, 4) not null,
    valor_total numeric(19, 4) not null,
    custo_ingredientes_total numeric(19, 4) not null,
    margem_bruta_estimada_total numeric(19, 4) not null,
    idempotency_key varchar(120),
    numero_caixa_legado integer,
    criada_em timestamp not null,
    criada_por bigint,
    finalizada_em timestamp,
    finalizada_por bigint,
    cancelada_em timestamp,
    cancelada_por bigint,
    motivo_cancelamento varchar(500),
    version bigint not null default 0,
    constraint uk_vendas_numero unique (numero),
    constraint ck_vendas_status check (status in ('RASCUNHO', 'FINALIZADA', 'CANCELADA', 'PARCIALMENTE_CANCELADA')),
    constraint ck_vendas_canal check (canal in ('SALAO', 'BALCAO', 'RETIRADA', 'DELIVERY_PROPRIO', 'APLICATIVO', 'MARKETPLACE', 'EVENTO', 'CORPORATIVA')),
    constraint ck_vendas_valores check (
        subtotal >= 0 and desconto_total >= 0 and acrescimo_total >= 0 and valor_total >= 0
        and custo_ingredientes_total >= 0
    )
);

create index if not exists idx_vendas_filial_status_criada
    on vendas (filial_id, status, criada_em desc);

create index if not exists idx_vendas_finalizada
    on vendas (finalizada_em desc);

create table if not exists venda_itens (
    id bigserial primary key,
    venda_id bigint not null references vendas(id),
    item_cardapio_id integer not null references itens_cardapio(id),
    variacao_item_cardapio_id bigint not null references variacoes_item_cardapio(id),
    quantidade numeric(19, 6) not null,
    nome_item_snapshot varchar(160) not null,
    nome_variacao_snapshot varchar(120) not null,
    codigo_variacao_snapshot varchar(80) not null,
    tipo_composicao_snapshot varchar(30) not null,
    preco_unitario_base numeric(19, 4) not null,
    desconto_unitario numeric(19, 4) not null,
    acrescimo_unitario numeric(19, 4) not null,
    preco_unitario_final numeric(19, 4) not null,
    subtotal numeric(19, 4) not null,
    custo_unitario_estimado numeric(19, 4) not null,
    custo_total_estimado numeric(19, 4) not null,
    margem_bruta_unitaria_estimada numeric(19, 4) not null,
    margem_bruta_total_estimada numeric(19, 4) not null,
    ficha_tecnica_id bigint references fichas_tecnicas(id),
    ficha_tecnica_versao_id bigint references fichas_tecnicas_versoes(id),
    numero_versao_ficha integer,
    status varchar(20) not null,
    observacao varchar(500),
    criado_em timestamp not null,
    cancelado_em timestamp,
    cancelado_por bigint,
    motivo_cancelamento varchar(500),
    version bigint not null default 0,
    constraint ck_venda_item_qtd check (quantidade > 0),
    constraint ck_venda_item_status check (status in ('ATIVO', 'CANCELADO')),
    constraint ck_venda_item_tipo check (tipo_composicao_snapshot in ('PRODUTO_DIRETO', 'PREPARADO')),
    constraint ck_venda_item_valores check (
        preco_unitario_base >= 0 and desconto_unitario >= 0 and acrescimo_unitario >= 0
        and preco_unitario_final >= 0 and subtotal >= 0 and custo_total_estimado >= 0
    )
);

create index if not exists idx_venda_itens_venda
    on venda_itens (venda_id, id);

create index if not exists idx_venda_itens_variacao
    on venda_itens (variacao_item_cardapio_id);

create index if not exists idx_venda_itens_ficha_versao
    on venda_itens (ficha_tecnica_versao_id);

create table if not exists venda_item_consumos (
    id bigserial primary key,
    venda_item_id bigint not null references venda_itens(id),
    produto_id integer not null references produtos(id),
    nome_produto_snapshot varchar(160) not null,
    unidade_base_id bigint not null references unidades_medida(id),
    unidade_base_codigo_snapshot varchar(40) not null,
    quantidade_base_por_unidade numeric(19, 6) not null,
    quantidade_base_total numeric(19, 6) not null,
    custo_medio_unitario_snapshot numeric(19, 8) not null,
    custo_total_snapshot numeric(19, 4) not null,
    origem_composicao varchar(30) not null,
    ficha_tecnica_ingrediente_id bigint references fichas_tecnicas_ingredientes(id),
    movimento_estoque_id bigint references movimentos_estoque_decimal(id),
    criado_em timestamp not null,
    constraint ck_venda_consumo_qtd check (quantidade_base_por_unidade > 0 and quantidade_base_total > 0),
    constraint ck_venda_consumo_custo check (custo_medio_unitario_snapshot >= 0 and custo_total_snapshot >= 0),
    constraint ck_venda_consumo_origem check (origem_composicao in ('PRODUTO_DIRETO', 'FICHA_TECNICA'))
);

create index if not exists idx_venda_consumos_item
    on venda_item_consumos (venda_item_id);

create index if not exists idx_venda_consumos_produto
    on venda_item_consumos (produto_id);

create index if not exists idx_venda_consumos_movimento
    on venda_item_consumos (movimento_estoque_id);

-- Fase 2 acrescenta novos tipos logicos em movimentos_estoque_decimal.
-- O ddl-auto=update nao atualiza check constraints existentes, por isso o
-- baseline futuro precisa substituir explicitamente o check criado na Fase 0.
alter table movimentos_estoque_decimal
    drop constraint if exists movimentos_estoque_decimal_tipo_check;

alter table movimentos_estoque_decimal
    add constraint movimentos_estoque_decimal_tipo_check
    check (tipo in (
        'ENTRADA_COMPRA',
        'DEVOLUCAO_COMPRA',
        'AJUSTE_POSITIVO',
        'AJUSTE_NEGATIVO',
        'ESTORNO_MOVIMENTO',
        'SAIDA_VENDA',
        'ESTORNO_VENDA'
    ));
