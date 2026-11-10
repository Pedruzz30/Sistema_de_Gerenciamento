-- Caixa PDV para varejo de balcão (depósito de bebidas).
--
-- 1) Formas de pagamento: a gaveta passa a somar só o dinheiro.
-- 2) Cancelamento de venda vira ESTORNO_VENDA (antes era lançado como SANGRIA).
-- 3) ID das movimentações de caixa gerado pelo banco (antes: contador em memória).
-- 4) Códigos de barras / códigos internos que levam direto à variação vendida.

-- 1 + 2) movimentacoes_caixa ---------------------------------------------------
alter table movimentacoes_caixa
    add column forma_pagamento varchar(20);

alter table movimentacoes_caixa
    add column venda_id bigint;

alter table movimentacoes_caixa
    drop constraint if exists ck_movimentacoes_caixa_tipo;

alter table movimentacoes_caixa
    add constraint ck_movimentacoes_caixa_tipo
    check (tipo in ('VENDA', 'SUPRIMENTO', 'ENTRADA', 'SANGRIA', 'DESPESA', 'ESTORNO_VENDA'));

alter table movimentacoes_caixa
    add constraint ck_movimentacoes_caixa_forma_pagamento
    check (forma_pagamento is null
        or forma_pagamento in ('DINHEIRO', 'PIX', 'DEBITO', 'CREDITO', 'VOUCHER'));

-- Vendas antigas não tinham forma de pagamento: tudo era tratado como dinheiro.
update movimentacoes_caixa
set forma_pagamento = 'DINHEIRO'
where tipo = 'VENDA'
  and forma_pagamento is null;

-- 3) ID gerado pelo banco ---------------------------------------------------------
create sequence if not exists movimentacoes_caixa_id_seq;

select setval(
    'movimentacoes_caixa_id_seq',
    greatest((select coalesce(max(id), 0) from movimentacoes_caixa), 0) + 1,
    false
);

alter table movimentacoes_caixa
    alter column id set default nextval('movimentacoes_caixa_id_seq');

alter sequence movimentacoes_caixa_id_seq owned by movimentacoes_caixa.id;

-- Pagamentos de cada venda ---------------------------------------------------------
create table venda_pagamentos (
    id bigserial primary key,
    venda_id bigint not null references vendas(id),
    forma varchar(20) not null,
    valor numeric(19, 2) not null,
    valor_recebido numeric(19, 2),
    troco numeric(19, 2) not null default 0,
    nsu varchar(40),
    criado_em timestamp not null,
    constraint ck_venda_pagamentos_forma
        check (forma in ('DINHEIRO', 'PIX', 'DEBITO', 'CREDITO', 'VOUCHER')),
    constraint ck_venda_pagamentos_valor check (valor > 0),
    constraint ck_venda_pagamentos_troco check (troco >= 0),
    constraint ck_venda_pagamentos_troco_dinheiro check (troco = 0 or forma = 'DINHEIRO')
);

create index idx_venda_pagamentos_venda on venda_pagamentos(venda_id, id);

-- 4) Códigos de venda (EAN/GTIN ou código interno) --------------------------------
create table codigos_venda (
    id bigserial primary key,
    codigo varchar(32) not null,
    tipo varchar(12) not null,
    variacao_id bigint not null references variacoes_item_cardapio(id),
    padrao boolean not null default true,
    ativo boolean not null default true,
    criado_em timestamp not null,
    constraint ck_codigos_venda_tipo check (tipo in ('EAN13', 'EAN8', 'GTIN14', 'UPCA', 'INTERNO')),
    constraint uk_codigos_venda_codigo_variacao unique (codigo, variacao_id)
);

-- Um código só pode ter uma variação padrão ativa (ex.: lata "Gelada");
-- as demais (ex.: "Natural") ficam como alternativas.
create unique index uk_codigos_venda_padrao_ativo
    on codigos_venda(codigo)
    where padrao and ativo;

create index idx_codigos_venda_variacao on codigos_venda(variacao_id);
