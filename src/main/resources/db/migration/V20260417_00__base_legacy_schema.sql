-- Base legada necessaria para banco vazio com Flyway.
-- Antes da Fase 4.6 estas tabelas eram criadas implicitamente por Hibernate ddl-auto=update.

create table produtos (
    id serial primary key,
    nome varchar(255) not null,
    quantidade_atual integer not null,
    quantidade_minima integer not null,
    preco_unitario numeric(19, 2) not null,
    categoria_estoque varchar(255),
    controlado_por_estoque boolean,
    constraint ck_produtos_quantidades_legadas check (quantidade_atual >= 0 and quantidade_minima >= 0),
    constraint ck_produtos_preco_unitario check (preco_unitario >= 0),
    constraint ck_produtos_categoria_estoque check (
        categoria_estoque is null or categoria_estoque in ('BEBIDAS', 'SECO', 'FRIO', 'CONGELADO')
    )
);

create table fornecedores (
    id bigserial primary key,
    nome varchar(160) not null,
    cnpj varchar(14) not null,
    telefone varchar(40),
    ativo boolean not null,
    constraint uk_fornecedores_cnpj unique (cnpj)
);

create table fornecedor_produtos (
    fornecedor_id bigint not null references fornecedores(id),
    produto_id integer not null references produtos(id),
    constraint uk_fornecedor_produtos_fornecedor_produto unique (fornecedor_id, produto_id)
);

create table usuarios (
    ru bigserial primary key,
    nome varchar(80) not null,
    sobrenome varchar(120) not null,
    cpf varchar(11) not null,
    senha varchar(120) not null,
    perfil varchar(20),
    classe_id bigint,
    classe_nome varchar(80),
    classe_descricao varchar(180),
    classe_permissoes varchar(250),
    ativo boolean not null,
    constraint uk_usuarios_cpf unique (cpf),
    constraint ck_usuarios_perfil check (perfil is null or perfil in ('ADMIN', 'OPERADOR'))
);

create table caixas (
    id bigserial primary key,
    numero_caixa integer not null,
    saldo_inicial numeric(19, 2) not null,
    saldo_atual numeric(19, 2) not null,
    status varchar(20) not null,
    operador_atual_ru bigint references usuarios(ru),
    data_abertura timestamp,
    data_encerramento timestamp,
    constraint ck_caixas_status check (status in ('FECHADO', 'ABERTO', 'ENCERRADO')),
    constraint ck_caixas_saldos check (saldo_inicial >= 0)
);

create table movimentacoes_caixa (
    id bigint primary key,
    tipo varchar(20) not null,
    valor numeric(19, 2) not null,
    descricao varchar(255),
    data_hora timestamp not null,
    operador_ru bigint not null references usuarios(ru),
    caixa_id bigint not null references caixas(id),
    constraint ck_movimentacoes_caixa_tipo check (tipo in ('VENDA', 'SUPRIMENTO', 'ENTRADA', 'SANGRIA', 'DESPESA')),
    constraint ck_movimentacoes_caixa_valor check (valor > 0)
);

create index idx_movimentacoes_caixa_caixa
    on movimentacoes_caixa(caixa_id, id);

create table logs_auditoria (
    id bigserial primary key,
    usuario_ru bigint,
    acao varchar(80) not null,
    descricao varchar(500) not null,
    data_hora timestamp not null
);

create table cotacoes_mensais (
    id bigserial primary key,
    produto_id integer not null references produtos(id),
    mes_referencia varchar(7) not null,
    preco_unitario numeric(19, 2) not null,
    quantidade_comprada integer not null,
    constraint uk_cotacoes_mensais_produto_mes unique (produto_id, mes_referencia),
    constraint ck_cotacoes_mensais_valores check (preco_unitario >= 0 and quantidade_comprada > 0)
);

create table movimentacoes_estoque (
    id serial primary key,
    produto_id integer not null references produtos(id),
    quantidade integer not null,
    tipo_movimentacao varchar(20) not null,
    data_hora timestamp not null,
    usuario_responsavel_ru bigint,
    usuario_responsavel_nome varchar(160),
    descricao varchar(500) not null,
    saldo_anterior integer not null,
    saldo_posterior integer not null,
    constraint ck_movimentacoes_estoque_tipo check (tipo_movimentacao in ('ENTRADA', 'SAIDA')),
    constraint ck_movimentacoes_estoque_quantidade check (quantidade > 0)
);

create table categorias_cardapio (
    id serial primary key,
    codigo varchar(80) not null,
    nome_exibicao varchar(120) not null,
    ordem_exibicao integer not null,
    ativo boolean not null,
    constraint uk_categorias_cardapio_codigo unique (codigo),
    constraint ck_categorias_cardapio_ordem check (ordem_exibicao >= 0)
);

create table itens_cardapio (
    id serial primary key,
    codigo varchar(120) not null,
    nome varchar(160) not null,
    descricao_curta varchar(280),
    preco_venda numeric(19, 2) not null,
    categoria_cardapio_id integer not null references categorias_cardapio(id),
    tipo_item varchar(40) not null,
    ativo boolean not null default true,
    disponivel boolean not null default true,
    ordem_exibicao integer not null,
    produto_vinculado_id integer references produtos(id),
    constraint uk_itens_cardapio_codigo unique (codigo),
    constraint ck_itens_cardapio_tipo check (tipo_item in ('ESTOQUE_DIRETO', 'PREPARADO_SOB_DEMANDA')),
    constraint ck_itens_cardapio_preco check (preco_venda >= 0),
    constraint ck_itens_cardapio_ordem check (ordem_exibicao >= 0)
);

create table notas_fiscais (
    id bigserial primary key,
    fornecedor_id bigint not null references fornecedores(id),
    usuario_responsavel_ru bigint not null references usuarios(ru),
    data_emissao timestamp not null,
    status varchar(20) not null,
    data_confirmacao timestamp,
    data_pagamento timestamp,
    data_cancelamento timestamp,
    constraint ck_notas_fiscais_status check (status in ('PENDENTE', 'CONFIRMADA', 'PAGA', 'CANCELADA'))
);

create table nota_fiscal_itens (
    id bigserial primary key,
    produto_id integer not null references produtos(id),
    quantidade integer not null,
    preco_unitario_nota numeric(19, 2) not null,
    nota_fiscal_id bigint not null references notas_fiscais(id),
    constraint ck_nota_fiscal_itens_quantidade check (quantidade >= 0),
    constraint ck_nota_fiscal_itens_preco check (preco_unitario_nota >= 0)
);

create index idx_nota_fiscal_itens_nota
    on nota_fiscal_itens(nota_fiscal_id, id);
