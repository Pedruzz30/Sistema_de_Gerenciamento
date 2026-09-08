-- Migration de referencia da Fase 1.
-- O projeto ainda usa spring.jpa.hibernate.ddl-auto=update; este arquivo documenta
-- o schema esperado para ativacao futura de Flyway/baseline, sem ser aplicado automaticamente.

create table if not exists variacoes_item_cardapio (
    id bigserial primary key,
    item_cardapio_id integer not null references itens_cardapio(id),
    nome varchar(120) not null,
    codigo varchar(80) not null,
    preco_venda numeric(19,2) not null,
    tipo_composicao varchar(30) not null,
    produto_direto_id integer references produtos(id),
    ativa boolean not null default true,
    padrao boolean not null default false,
    ordem_exibicao integer not null default 0,
    criado_em timestamp not null,
    atualizado_em timestamp not null,
    version bigint not null default 0,
    constraint uk_variacao_item_codigo unique (item_cardapio_id, codigo),
    constraint ck_variacao_preco_nao_negativo check (preco_venda >= 0),
    constraint ck_variacao_ordem_nao_negativa check (ordem_exibicao >= 0),
    constraint ck_variacao_tipo check (tipo_composicao in ('PRODUTO_DIRETO', 'PREPARADO')),
    constraint ck_variacao_composicao_consistente check (
        (tipo_composicao = 'PRODUTO_DIRETO' and produto_direto_id is not null)
        or
        (tipo_composicao = 'PREPARADO' and produto_direto_id is null)
    )
);

create unique index if not exists uk_variacao_padrao_ativa_item
    on variacoes_item_cardapio(item_cardapio_id)
    where padrao = true and ativa = true;

create index if not exists idx_variacao_item on variacoes_item_cardapio(item_cardapio_id);
create index if not exists idx_variacao_produto_direto on variacoes_item_cardapio(produto_direto_id);

create table if not exists fichas_tecnicas (
    id bigserial primary key,
    variacao_item_cardapio_id bigint not null references variacoes_item_cardapio(id),
    nome varchar(160) not null,
    ativa boolean not null default true,
    versao_publicada_atual_id bigint null,
    criada_em timestamp not null,
    criada_por bigint null,
    atualizado_em timestamp not null,
    version bigint not null default 0
);

create unique index if not exists uk_ficha_ativa_variacao
    on fichas_tecnicas(variacao_item_cardapio_id)
    where ativa = true;

create index if not exists idx_ficha_variacao on fichas_tecnicas(variacao_item_cardapio_id);

create table if not exists fichas_tecnicas_versoes (
    id bigserial primary key,
    ficha_tecnica_id bigint not null references fichas_tecnicas(id),
    numero_versao integer not null,
    status varchar(30) not null,
    rendimento_quantidade numeric(19,6) not null,
    rendimento_unidade_id bigint not null references unidades_medida(id),
    quantidade_por_porcao numeric(19,6) null,
    tempo_preparo_minutos integer null,
    observacao_interna varchar(1000) null,
    motivo_alteracao varchar(500) null,
    criada_em timestamp not null,
    criada_por bigint null,
    publicada_em timestamp null,
    publicada_por bigint null,
    substituida_em timestamp null,
    version bigint not null default 0,
    constraint uk_ficha_versao_numero unique (ficha_tecnica_id, numero_versao),
    constraint ck_ficha_status check (status in ('RASCUNHO', 'PUBLICADA', 'SUBSTITUIDA', 'DESCARTADA')),
    constraint ck_ficha_rendimento_pos check (rendimento_quantidade > 0),
    constraint ck_ficha_porcao_pos check (quantidade_por_porcao is null or quantidade_por_porcao > 0),
    constraint ck_ficha_tempo_nao_negativo check (tempo_preparo_minutos is null or tempo_preparo_minutos >= 0)
);

alter table fichas_tecnicas
    add constraint fk_ficha_versao_publicada_atual
    foreign key (versao_publicada_atual_id) references fichas_tecnicas_versoes(id);

create unique index if not exists uk_ficha_publicada_vigente
    on fichas_tecnicas_versoes(ficha_tecnica_id)
    where status = 'PUBLICADA';

create unique index if not exists uk_ficha_rascunho_ativo
    on fichas_tecnicas_versoes(ficha_tecnica_id)
    where status = 'RASCUNHO';

create index if not exists idx_ficha_versao_ficha on fichas_tecnicas_versoes(ficha_tecnica_id);
create index if not exists idx_ficha_versao_status on fichas_tecnicas_versoes(status);

create table if not exists fichas_tecnicas_ingredientes (
    id bigserial primary key,
    ficha_tecnica_versao_id bigint not null references fichas_tecnicas_versoes(id),
    produto_id integer not null references produtos(id),
    unidade_informada_id bigint not null references unidades_medida(id),
    quantidade_informada numeric(19,6) not null,
    quantidade_liquida_base numeric(19,6) null,
    percentual_perda numeric(9,4) null,
    quantidade_bruta_base numeric(19,6) not null,
    ingrediente_critico boolean not null default false,
    politica_falta varchar(30) not null,
    ordem_preparo integer not null default 0,
    observacao varchar(1000) null,
    criado_em timestamp not null,
    constraint ck_ingrediente_qtd_informada_pos check (quantidade_informada > 0),
    constraint ck_ingrediente_qtd_liquida_pos check (quantidade_liquida_base is null or quantidade_liquida_base > 0),
    constraint ck_ingrediente_qtd_bruta_pos check (quantidade_bruta_base > 0),
    constraint ck_ingrediente_perda check (percentual_perda is null or (percentual_perda >= 0 and percentual_perda < 100)),
    constraint ck_ingrediente_politica check (politica_falta in ('HERDAR_PRODUTO', 'BLOQUEAR', 'ALERTAR', 'IGNORAR')),
    constraint ck_ingrediente_ordem_nao_negativa check (ordem_preparo >= 0)
);

create index if not exists idx_ficha_ingrediente_versao on fichas_tecnicas_ingredientes(ficha_tecnica_versao_id);
create index if not exists idx_ficha_ingrediente_produto on fichas_tecnicas_ingredientes(produto_id);

create table if not exists fichas_tecnicas_custo_snapshot (
    id bigserial primary key,
    ficha_tecnica_versao_id bigint not null references fichas_tecnicas_versoes(id),
    filial_id bigint not null,
    custo_total numeric(19,4) not null,
    custo_por_unidade_rendimento numeric(19,4) not null,
    calculado_em timestamp not null,
    constraint ck_snapshot_custo_total_nao_negativo check (custo_total >= 0),
    constraint ck_snapshot_custo_unidade_nao_negativo check (custo_por_unidade_rendimento >= 0)
);

create index if not exists idx_snapshot_custo_versao_filial
    on fichas_tecnicas_custo_snapshot(ficha_tecnica_versao_id, filial_id);

create table if not exists fichas_tecnicas_ingredientes_custo_snapshot (
    id bigserial primary key,
    ficha_tecnica_custo_snapshot_id bigint not null references fichas_tecnicas_custo_snapshot(id),
    ficha_tecnica_ingrediente_id bigint not null references fichas_tecnicas_ingredientes(id),
    produto_id integer not null references produtos(id),
    quantidade_base numeric(19,6) not null,
    custo_medio_unitario numeric(19,8) not null,
    custo_total_ingrediente numeric(19,4) not null,
    constraint ck_snapshot_ingrediente_qtd_pos check (quantidade_base > 0),
    constraint ck_snapshot_ingrediente_custo_unit_nao_negativo check (custo_medio_unitario >= 0),
    constraint ck_snapshot_ingrediente_custo_total_nao_negativo check (custo_total_ingrediente >= 0)
);

create index if not exists idx_snapshot_ingrediente_snapshot
    on fichas_tecnicas_ingredientes_custo_snapshot(ficha_tecnica_custo_snapshot_id);

create index if not exists idx_snapshot_ingrediente_produto
    on fichas_tecnicas_ingredientes_custo_snapshot(produto_id);
