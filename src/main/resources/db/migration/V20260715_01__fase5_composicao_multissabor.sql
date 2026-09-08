-- Fase 5 - itens meio a meio e composicao multissabor.

create table configuracoes_composicao_multipla (
    id bigserial primary key,
    variacao_item_cardapio_id bigint not null references variacoes_item_cardapio(id),
    ativo boolean not null default false,
    minimo_partes integer not null,
    maximo_partes integer not null,
    quantidades_permitidas varchar(120),
    permite_repetir_partes boolean not null default false,
    proporcoes_iguais_obrigatorias boolean not null default true,
    permite_proporcao_personalizada boolean not null default false,
    politica_preco varchar(40) not null,
    politica_arredondamento varchar(40) not null,
    criado_em timestamp not null,
    atualizado_em timestamp not null,
    version bigint not null default 0,
    constraint uk_config_composicao_multipla_variacao unique (variacao_item_cardapio_id),
    constraint ck_config_composicao_partes check (
        minimo_partes >= 1
        and maximo_partes >= minimo_partes
    ),
    constraint ck_config_composicao_quantidades check (
        quantidades_permitidas is null
        or quantidades_permitidas ~ '^[0-9]+(,[0-9]+)*$'
    ),
    constraint ck_config_composicao_politica_preco check (
        politica_preco in (
            'MAIOR_PRECO',
            'MEDIA_ARITMETICA',
            'MEDIA_PONDERADA',
            'SOMA_PROPORCIONAL',
            'PRECO_FIXO_DA_VARIACAO'
        )
    ),
    constraint ck_config_composicao_arredondamento check (
        politica_arredondamento in ('HALF_UP')
    )
);

create index idx_config_composicao_variacao_ativo
    on configuracoes_composicao_multipla (variacao_item_cardapio_id, ativo);

create table configuracao_composicao_multipla_partes (
    id bigserial primary key,
    configuracao_id bigint not null references configuracoes_composicao_multipla(id),
    variacao_parte_id bigint not null references variacoes_item_cardapio(id),
    ativa boolean not null default true,
    ordem_exibicao integer not null default 0,
    criado_em timestamp not null,
    constraint uk_config_composicao_multipla_parte unique (configuracao_id, variacao_parte_id),
    constraint ck_config_composicao_parte_ordem check (ordem_exibicao >= 0)
);

create index idx_config_composicao_partes_config
    on configuracao_composicao_multipla_partes (configuracao_id, ativa, ordem_exibicao);

create index idx_config_composicao_partes_variacao
    on configuracao_composicao_multipla_partes (variacao_parte_id);

alter table venda_itens
    add column composicao_multipla boolean not null default false,
    add column politica_preco_composicao varchar(40),
    add column quantidade_partes_snapshot integer,
    add column resumo_partes_snapshot varchar(1000),
    add column configuracao_multissabor_hash_observada varchar(128);

alter table venda_itens
    add constraint ck_venda_item_composicao_multipla check (
        (
            composicao_multipla = false
            and politica_preco_composicao is null
            and quantidade_partes_snapshot is null
            and resumo_partes_snapshot is null
            and configuracao_multissabor_hash_observada is null
        )
        or
        (
            composicao_multipla = true
            and politica_preco_composicao in (
                'MAIOR_PRECO',
                'MEDIA_ARITMETICA',
                'MEDIA_PONDERADA',
                'SOMA_PROPORCIONAL',
                'PRECO_FIXO_DA_VARIACAO'
            )
            and quantidade_partes_snapshot > 0
            and configuracao_multissabor_hash_observada is not null
        )
    );

create table venda_item_partes (
    id bigserial primary key,
    venda_item_id bigint not null references venda_itens(id),
    variacao_parte_id bigint not null references variacoes_item_cardapio(id),
    item_cardapio_parte_id integer not null references itens_cardapio(id),
    nome_item_snapshot varchar(160) not null,
    nome_variacao_snapshot varchar(120) not null,
    codigo_variacao_snapshot varchar(80) not null,
    tipo_composicao_snapshot varchar(30) not null,
    proporcao numeric(19, 6) not null,
    preco_referencia_snapshot numeric(19, 4) not null,
    custo_referencia_snapshot numeric(19, 4) not null,
    ficha_tecnica_id bigint references fichas_tecnicas(id),
    ficha_tecnica_versao_id bigint references fichas_tecnicas_versoes(id),
    numero_versao_ficha integer,
    configuracao_hash_snapshot varchar(128),
    ordem_visual integer not null,
    status varchar(20) not null,
    criado_em timestamp not null,
    constraint ck_venda_item_parte_tipo check (tipo_composicao_snapshot in ('PRODUTO_DIRETO', 'PREPARADO')),
    constraint ck_venda_item_parte_proporcao check (proporcao > 0 and proporcao <= 1),
    constraint ck_venda_item_parte_valores check (
        preco_referencia_snapshot >= 0
        and custo_referencia_snapshot >= 0
    ),
    constraint ck_venda_item_parte_ordem check (ordem_visual >= 0),
    constraint ck_venda_item_parte_status check (status in ('ATIVO', 'CANCELADO')),
    constraint ck_venda_item_parte_ficha check (
        (ficha_tecnica_id is null and ficha_tecnica_versao_id is null and numero_versao_ficha is null)
        or
        (ficha_tecnica_id is not null and ficha_tecnica_versao_id is not null and numero_versao_ficha is not null)
    )
);

create index idx_venda_item_partes_item
    on venda_item_partes (venda_item_id, ordem_visual, id);

create index idx_venda_item_partes_venda
    on venda_item_partes (variacao_parte_id, status);

alter table venda_item_modificadores
    add column venda_item_parte_id bigint references venda_item_partes(id);

create index idx_venda_item_modificadores_parte
    on venda_item_modificadores (venda_item_parte_id, id)
    where venda_item_parte_id is not null;

alter table venda_item_consumos
    add column venda_item_parte_id bigint references venda_item_partes(id);

create index idx_venda_consumos_parte
    on venda_item_consumos (venda_item_parte_id, id)
    where venda_item_parte_id is not null;
