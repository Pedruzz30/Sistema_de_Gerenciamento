-- Fase 0 - fundacao de estoque decimal, unidades, compras e custo medio.
-- O projeto ainda nao possui Flyway/Liquibase no pom.xml; este arquivo documenta
-- a evolucao esperada enquanto a criacao real do schema segue Hibernate ddl-auto=update.

create table if not exists unidades_medida (
    id bigserial primary key,
    codigo varchar(40) not null,
    nome varchar(120) not null,
    dimensao varchar(20) not null,
    fator_base numeric(19, 8) not null,
    ativa boolean not null,
    constraint uk_unidades_medida_codigo unique (codigo),
    constraint ck_unidades_medida_fator_base check (fator_base > 0),
    constraint ck_unidades_medida_dimensao check (dimensao in ('MASSA', 'VOLUME', 'CONTAGEM'))
);

alter table produtos
    add column if not exists unidade_base_id bigint references unidades_medida (id);

alter table produtos
    add column if not exists politica_falta varchar(20) not null default 'BLOQUEAR';

alter table produtos
    add column if not exists ativo boolean not null default true;

create table if not exists embalagens_produto (
    id bigserial primary key,
    produto_id integer not null references produtos (id),
    nome varchar(120) not null,
    quantidade_base numeric(19, 6) not null,
    unidade_base_id bigint not null references unidades_medida (id),
    codigo_barras varchar(80),
    ativa boolean not null,
    constraint uk_embalagens_produto_nome unique (produto_id, nome),
    constraint ck_embalagens_produto_qtd check (quantidade_base > 0)
);

create table if not exists estoques_produto (
    id bigserial primary key,
    filial_id bigint not null,
    produto_id integer not null references produtos (id),
    quantidade_atual numeric(19, 6) not null,
    quantidade_reservada numeric(19, 6) not null,
    custo_medio numeric(19, 8) not null,
    version bigint not null,
    atualizado_em timestamp not null,
    constraint uk_estoques_produto_filial_produto unique (filial_id, produto_id),
    constraint ck_estoques_produto_reservada check (quantidade_reservada >= 0),
    constraint ck_estoques_produto_custo check (custo_medio >= 0)
);

create table if not exists compras_estoque (
    id bigserial primary key,
    filial_id bigint not null,
    fornecedor_id bigint references fornecedores (id),
    status varchar(20) not null,
    usuario_criacao_id bigint,
    usuario_confirmacao_id bigint,
    data_criacao timestamp not null,
    data_confirmacao timestamp,
    idempotency_key varchar(120),
    payload_hash varchar(128)
);

create table if not exists itens_compra_estoque (
    id bigserial primary key,
    compra_id bigint not null references compras_estoque (id),
    produto_id integer not null references produtos (id),
    embalagem_id bigint references embalagens_produto (id),
    unidade_compra_id bigint references unidades_medida (id),
    quantidade_comprada numeric(19, 6) not null,
    quantidade_base numeric(19, 6) not null,
    custo_total numeric(19, 4) not null,
    custo_unitario_compra numeric(19, 8) not null,
    custo_unitario_base numeric(19, 8) not null,
    constraint ck_itens_compra_quantidade check (quantidade_comprada > 0 and quantidade_base > 0),
    constraint ck_itens_compra_custo check (custo_total >= 0),
    constraint ck_itens_compra_origem_unidade check (
        (embalagem_id is not null and unidade_compra_id is null)
        or (embalagem_id is null and unidade_compra_id is not null)
    )
);

create table if not exists movimentos_estoque_decimal (
    id bigserial primary key,
    filial_id bigint not null,
    produto_id integer not null references produtos (id),
    tipo varchar(40) not null,
    quantidade_base numeric(19, 6) not null,
    saldo_anterior numeric(19, 6) not null,
    saldo_posterior numeric(19, 6) not null,
    custo_unitario numeric(19, 8) not null,
    custo_total numeric(19, 4) not null,
    custo_medio_anterior numeric(19, 8),
    custo_medio_posterior numeric(19, 8),
    valor_estoque_anterior numeric(19, 4),
    valor_estoque_posterior numeric(19, 4),
    origem_tipo varchar(60) not null,
    origem_id bigint not null,
    usuario_id bigint,
    motivo varchar(240) not null,
    observacao varchar(500),
    idempotency_key varchar(120),
    movimento_estornado_id bigint references movimentos_estoque_decimal (id),
    criado_em timestamp not null,
    constraint uk_movimentos_estoque_estorno_unico unique (movimento_estornado_id),
    constraint ck_movimentos_estoque_quantidade check (quantidade_base > 0),
    constraint ck_movimentos_estoque_custo check (custo_unitario >= 0 and custo_total >= 0)
);

create table if not exists registros_idempotencia (
    id bigserial primary key,
    filial_id bigint not null,
    operacao varchar(80) not null,
    idempotency_key varchar(120) not null,
    payload_hash varchar(128) not null,
    recurso_id bigint,
    status varchar(20) not null default 'CONCLUIDA',
    criado_em timestamp not null,
    constraint uk_registros_idempotencia_escopo unique (filial_id, operacao, idempotency_key),
    constraint ck_registros_idempotencia_status check (status in ('PROCESSANDO', 'CONCLUIDA')),
    constraint ck_registros_idempotencia_recurso_concluido check (status <> 'CONCLUIDA' or recurso_id is not null)
);

alter table movimentos_estoque_decimal
    add column if not exists custo_medio_anterior numeric(19, 8);

alter table movimentos_estoque_decimal
    add column if not exists custo_medio_posterior numeric(19, 8);

alter table movimentos_estoque_decimal
    add column if not exists valor_estoque_anterior numeric(19, 4);

alter table movimentos_estoque_decimal
    add column if not exists valor_estoque_posterior numeric(19, 4);

alter table registros_idempotencia
    add column if not exists status varchar(20) not null default 'CONCLUIDA';

alter table registros_idempotencia
    alter column recurso_id drop not null;

create index if not exists idx_movimentos_estoque_decimal_produto_criado
    on movimentos_estoque_decimal (produto_id, criado_em desc);

create index if not exists idx_compras_estoque_status
    on compras_estoque (status);
