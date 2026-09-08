-- Fase 3 - disponibilidade operacional, capacidade estimada, pausas, horarios e canal.
-- O projeto ainda usa ddl-auto=update; esta migration documenta o schema real
-- esperado para baseline futuro do Flyway.

alter table variacoes_item_cardapio
    add column if not exists limite_alerta_capacidade integer;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_variacoes_limite_alerta_capacidade'
    ) then
        alter table variacoes_item_cardapio
            add constraint ck_variacoes_limite_alerta_capacidade
            check (limite_alerta_capacidade is null or limite_alerta_capacidade >= 0);
    end if;
end $$;

create table if not exists pausas_cardapio (
    id bigserial primary key,
    filial_id bigint not null,
    item_cardapio_id integer references itens_cardapio(id),
    variacao_item_cardapio_id bigint references variacoes_item_cardapio(id),
    canal varchar(40),
    inicio timestamp not null,
    fim timestamp,
    motivo_codigo varchar(40) not null,
    motivo_descricao varchar(500),
    ativa boolean not null,
    criada_em timestamp not null,
    criada_por bigint,
    encerrada_em timestamp,
    encerrada_por bigint,
    version bigint not null default 0,
    constraint ck_pausas_cardapio_alvo check (
        item_cardapio_id is not null or variacao_item_cardapio_id is not null
    ),
    constraint ck_pausas_cardapio_periodo check (
        fim is null or fim > inicio
    ),
    constraint ck_pausas_cardapio_canal check (
        canal is null or canal in (
            'SALAO',
            'BALCAO',
            'RETIRADA',
            'DELIVERY_PROPRIO',
            'APLICATIVO',
            'MARKETPLACE',
            'EVENTO',
            'CORPORATIVA'
        )
    ),
    constraint ck_pausas_cardapio_motivo check (
        motivo_codigo in (
            'FALTA_DE_INGREDIENTE',
            'MANUTENCAO',
            'EQUIPE_INDISPONIVEL',
            'SOBRECARGA_DA_COZINHA',
            'DECISAO_GERENCIAL',
            'OUTRO'
        )
    )
);

create index if not exists idx_pausas_cardapio_filial_ativas
    on pausas_cardapio (filial_id, ativa, inicio, fim);

create index if not exists idx_pausas_cardapio_item
    on pausas_cardapio (item_cardapio_id)
    where item_cardapio_id is not null;

create index if not exists idx_pausas_cardapio_variacao
    on pausas_cardapio (variacao_item_cardapio_id)
    where variacao_item_cardapio_id is not null;

create table if not exists regras_horario_cardapio (
    id bigserial primary key,
    filial_id bigint not null,
    item_cardapio_id integer references itens_cardapio(id),
    variacao_item_cardapio_id bigint references variacoes_item_cardapio(id),
    canal varchar(40),
    dia_semana varchar(20) not null,
    hora_inicio time not null,
    hora_fim time not null,
    ativa boolean not null,
    version bigint not null default 0,
    constraint ck_regras_horario_alvo check (
        item_cardapio_id is not null or variacao_item_cardapio_id is not null
    ),
    constraint ck_regras_horario_intervalo check (
        hora_inicio <> hora_fim
    ),
    constraint ck_regras_horario_canal check (
        canal is null or canal in (
            'SALAO',
            'BALCAO',
            'RETIRADA',
            'DELIVERY_PROPRIO',
            'APLICATIVO',
            'MARKETPLACE',
            'EVENTO',
            'CORPORATIVA'
        )
    ),
    constraint ck_regras_horario_dia check (
        dia_semana in (
            'MONDAY',
            'TUESDAY',
            'WEDNESDAY',
            'THURSDAY',
            'FRIDAY',
            'SATURDAY',
            'SUNDAY'
        )
    )
);

create index if not exists idx_regras_horario_filial
    on regras_horario_cardapio (filial_id, ativa, dia_semana);

create index if not exists idx_regras_horario_variacao
    on regras_horario_cardapio (variacao_item_cardapio_id, canal, dia_semana)
    where variacao_item_cardapio_id is not null;

create index if not exists idx_regras_horario_item
    on regras_horario_cardapio (item_cardapio_id, canal, dia_semana)
    where item_cardapio_id is not null;

create table if not exists variacao_canal_disponibilidade (
    id bigserial primary key,
    variacao_item_cardapio_id bigint not null references variacoes_item_cardapio(id),
    filial_id bigint not null,
    canal varchar(40) not null,
    permitida boolean not null,
    criado_em timestamp not null,
    atualizado_em timestamp not null,
    version bigint not null default 0,
    constraint uk_variacao_canal_filial unique (variacao_item_cardapio_id, filial_id, canal),
    constraint ck_variacao_canal_disponibilidade_canal check (
        canal in (
            'SALAO',
            'BALCAO',
            'RETIRADA',
            'DELIVERY_PROPRIO',
            'APLICATIVO',
            'MARKETPLACE',
            'EVENTO',
            'CORPORATIVA'
        )
    )
);

create index if not exists idx_variacao_canal_filial
    on variacao_canal_disponibilidade (filial_id, canal, permitida);
