-- Fase 4 - grupos de modificadores, opcoes, deltas de composicao e snapshots.
-- O projeto ainda usa ddl-auto=update; esta migration documenta o schema real
-- esperado para baseline futuro do Flyway.

create table if not exists grupos_modificadores (
    id bigserial primary key,
    nome varchar(120) not null,
    descricao varchar(500),
    codigo varchar(80) not null,
    tipo_selecao varchar(30) not null,
    obrigatorio boolean not null,
    quantidade_minima integer not null,
    quantidade_maxima integer not null,
    permite_repeticao boolean not null,
    ativo boolean not null,
    ordem_exibicao integer not null,
    criado_em timestamp not null,
    criado_por bigint,
    atualizado_em timestamp not null,
    version bigint not null default 0,
    constraint uk_grupo_modificador_codigo unique (codigo),
    constraint ck_grupo_modificador_tipo check (tipo_selecao in ('UNICA', 'MULTIPLA', 'QUANTIDADE')),
    constraint ck_grupo_modificador_quantidades check (
        quantidade_minima >= 0
        and quantidade_maxima >= quantidade_minima
        and ordem_exibicao >= 0
    ),
    constraint ck_grupo_modificador_unica check (
        tipo_selecao <> 'UNICA' or quantidade_maxima = 1
    ),
    constraint ck_grupo_modificador_obrigatorio check (
        obrigatorio = false or quantidade_minima >= 1
    )
);

create table if not exists opcoes_modificadores (
    id bigserial primary key,
    grupo_modificador_id bigint not null references grupos_modificadores(id),
    nome varchar(120) not null,
    descricao varchar(500),
    codigo varchar(80) not null,
    acrescimo_preco numeric(19, 4) not null,
    quantidade_maxima_por_item integer,
    selecionada_por_padrao boolean not null,
    ativa boolean not null,
    ordem_exibicao integer not null,
    criado_em timestamp not null,
    criado_por bigint,
    atualizado_em timestamp not null,
    version bigint not null default 0,
    constraint uk_opcao_modificador_grupo_codigo unique (grupo_modificador_id, codigo),
    constraint ck_opcao_modificador_valores check (
        acrescimo_preco >= 0
        and (quantidade_maxima_por_item is null or quantidade_maxima_por_item > 0)
        and ordem_exibicao >= 0
    )
);

create index if not exists idx_opcoes_modificadores_grupo
    on opcoes_modificadores (grupo_modificador_id, ativa, ordem_exibicao);

create table if not exists variacao_grupos_modificadores (
    id bigserial primary key,
    variacao_item_cardapio_id bigint not null references variacoes_item_cardapio(id),
    grupo_modificador_id bigint not null references grupos_modificadores(id),
    obrigatorio_override boolean,
    quantidade_minima_override integer,
    quantidade_maxima_override integer,
    ordem_exibicao integer not null,
    ativo boolean not null,
    criado_em timestamp not null,
    atualizado_em timestamp not null,
    version bigint not null default 0,
    constraint uk_variacao_grupo_modificador unique (variacao_item_cardapio_id, grupo_modificador_id),
    constraint ck_variacao_grupo_modificador_quantidades check (
        (quantidade_minima_override is null or quantidade_minima_override >= 0)
        and (quantidade_maxima_override is null or quantidade_maxima_override >= 0)
        and (
            quantidade_minima_override is null
            or quantidade_maxima_override is null
            or quantidade_maxima_override >= quantidade_minima_override
        )
        and ordem_exibicao >= 0
    )
);

create index if not exists idx_variacao_grupos_modificadores_variacao
    on variacao_grupos_modificadores (variacao_item_cardapio_id, ativo, ordem_exibicao);

create table if not exists opcoes_modificadores_componentes (
    id bigserial primary key,
    opcao_modificador_id bigint not null references opcoes_modificadores(id),
    tipo_efeito varchar(40) not null,
    produto_origem_id integer references produtos(id),
    produto_destino_id integer references produtos(id),
    quantidade_base numeric(19, 6),
    unidade_base_id bigint references unidades_medida(id),
    multiplicador numeric(19, 6),
    politica_falta varchar(30) not null,
    observacao varchar(1000),
    ordem integer not null,
    criado_em timestamp not null,
    constraint ck_opcao_componente_tipo check (
        tipo_efeito in ('ADICIONAR', 'REMOVER', 'SUBSTITUIR', 'SEM_IMPACTO_ESTOQUE')
    ),
    constraint ck_opcao_componente_politica check (
        politica_falta in ('HERDAR_PRODUTO', 'BLOQUEAR', 'ALERTAR', 'IGNORAR')
    ),
    constraint ck_opcao_componente_quantidade check (
        (
            tipo_efeito = 'SEM_IMPACTO_ESTOQUE'
            and produto_origem_id is null
            and produto_destino_id is null
            and quantidade_base is null
            and unidade_base_id is null
        )
        or (
            tipo_efeito <> 'SEM_IMPACTO_ESTOQUE'
            and quantidade_base > 0
            and unidade_base_id is not null
        )
    ),
    constraint ck_opcao_componente_multiplicador check (
        multiplicador is null or multiplicador > 0
    ),
    constraint ck_opcao_componente_ordem check (ordem >= 0)
);

create index if not exists idx_opcoes_modificadores_componentes_opcao
    on opcoes_modificadores_componentes (opcao_modificador_id, ordem);

create table if not exists incompatibilidades_opcoes_modificadores (
    id bigserial primary key,
    opcao_origem_id bigint not null references opcoes_modificadores(id),
    opcao_incompativel_id bigint not null references opcoes_modificadores(id),
    motivo varchar(500),
    ativa boolean not null,
    criada_em timestamp not null,
    constraint uk_incompatibilidade_opcoes unique (opcao_origem_id, opcao_incompativel_id),
    constraint ck_incompatibilidade_opcoes_distintas check (opcao_origem_id <> opcao_incompativel_id)
);

create index if not exists idx_incompatibilidades_opcoes_origem
    on incompatibilidades_opcoes_modificadores (opcao_origem_id, ativa);

create index if not exists idx_incompatibilidades_opcoes_destino
    on incompatibilidades_opcoes_modificadores (opcao_incompativel_id, ativa);

alter table venda_itens
    add column if not exists configuracao_modificadores_hash_observada varchar(128);

create table if not exists venda_item_modificadores (
    id bigserial primary key,
    venda_item_id bigint not null references venda_itens(id),
    grupo_modificador_id bigint not null references grupos_modificadores(id),
    opcao_modificador_id bigint not null references opcoes_modificadores(id),
    nome_grupo_snapshot varchar(120) not null,
    nome_opcao_snapshot varchar(120) not null,
    codigo_grupo_snapshot varchar(80) not null,
    codigo_opcao_snapshot varchar(80) not null,
    quantidade_selecionada integer not null,
    selecao_padrao boolean not null,
    acrescimo_unitario_snapshot numeric(19, 4) not null,
    acrescimo_total_snapshot numeric(19, 4) not null,
    criado_em timestamp not null,
    constraint ck_venda_item_modificador_qtd check (quantidade_selecionada > 0),
    constraint ck_venda_item_modificador_valores check (
        acrescimo_unitario_snapshot >= 0 and acrescimo_total_snapshot >= 0
    )
);

create index if not exists idx_venda_item_modificadores_item
    on venda_item_modificadores (venda_item_id, id);

create index if not exists idx_venda_item_modificadores_opcao
    on venda_item_modificadores (opcao_modificador_id);

create table if not exists venda_item_modificador_impactos (
    id bigserial primary key,
    venda_item_modificador_id bigint not null references venda_item_modificadores(id),
    venda_item_consumo_id bigint references venda_item_consumos(id),
    produto_id integer not null references produtos(id),
    tipo_efeito varchar(40) not null,
    quantidade_delta_base numeric(19, 6) not null,
    custo_unitario_snapshot numeric(19, 8) not null,
    valor_impacto_snapshot numeric(19, 4) not null,
    criado_em timestamp not null,
    constraint ck_venda_item_modificador_impacto_tipo check (
        tipo_efeito in ('ADICIONAR', 'REMOVER', 'SUBSTITUIR', 'SEM_IMPACTO_ESTOQUE')
    ),
    constraint ck_venda_item_modificador_impacto_delta check (quantidade_delta_base <> 0),
    constraint ck_venda_item_modificador_impacto_custo check (custo_unitario_snapshot >= 0)
);

create index if not exists idx_venda_item_modificador_impactos_modificador
    on venda_item_modificador_impactos (venda_item_modificador_id);

create index if not exists idx_venda_item_modificador_impactos_consumo
    on venda_item_modificador_impactos (venda_item_consumo_id)
    where venda_item_consumo_id is not null;

alter table venda_item_consumos
    drop constraint if exists ck_venda_consumo_origem;

alter table venda_item_consumos
    add constraint ck_venda_consumo_origem
    check (origem_composicao in (
        'PRODUTO_DIRETO',
        'FICHA_TECNICA',
        'MODIFICADOR_ADICAO',
        'MODIFICADOR_REMOCAO',
        'MODIFICADOR_SUBSTITUICAO'
    ));
