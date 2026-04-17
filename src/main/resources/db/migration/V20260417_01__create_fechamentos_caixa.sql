create table if not exists fechamentos_caixa (
    id bigserial primary key,
    caixa_id bigint not null references caixas (id),
    numero_caixa integer not null,
    aberto_por varchar(120) not null,
    fechado_por varchar(120) not null,
    quantidade_movimentacoes integer not null,
    saldo_inicial numeric(19, 2) not null,
    total_entradas numeric(19, 2) not null,
    total_saidas numeric(19, 2) not null,
    total_vendas numeric(19, 2) not null,
    valor_sistema numeric(19, 2) not null,
    valor_contado numeric(19, 2) not null,
    divergencia numeric(19, 2) not null,
    observacao varchar(500),
    timestamp_fechamento timestamp not null
);

create index if not exists idx_fechamentos_caixa_caixa_id
    on fechamentos_caixa (caixa_id);

create index if not exists idx_fechamentos_caixa_numero_timestamp
    on fechamentos_caixa (numero_caixa, timestamp_fechamento desc);
