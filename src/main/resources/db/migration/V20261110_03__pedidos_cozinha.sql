-- Pedido impresso para a cozinha. Cada impressão fica guardada: para não mandar o mesmo item
-- duas vezes, para reimprimir e para mandar o aviso de cancelamento quando um item sai.
create table configuracoes (
    chave varchar(80) primary key,
    valor text not null
);

create table pedidos_cozinha (
    id bigserial primary key,
    venda_id bigint references vendas(id),
    comanda_numero integer,
    tipo varchar(20) not null,
    texto text not null,
    criado_em timestamp not null,
    criado_por bigint,
    impresso_em timestamp,
    tentativas integer not null default 0,
    proxima_tentativa timestamp,
    erro varchar(300),
    constraint ck_pedidos_cozinha_tipo check (tipo in ('PEDIDO', 'CANCELAMENTO', 'TESTE'))
);

-- Quanto de cada item da venda já foi para a cozinha. Sem chave estrangeira: o item pode ser
-- apagado da conta, e o registro do que já foi impresso continua. pedido_id vazio = ajuste
-- (item que mudou de comanda levando o que já tinha sido impresso).
create table pedidos_cozinha_itens (
    id bigserial primary key,
    pedido_id bigint references pedidos_cozinha(id),
    venda_item_id bigint not null,
    descricao varchar(300) not null,
    quantidade numeric(19, 4) not null
);

create index idx_pedidos_cozinha_venda on pedidos_cozinha(venda_id);
create index idx_pedidos_cozinha_pendentes on pedidos_cozinha(impresso_em) where impresso_em is null;
create index idx_pedidos_cozinha_itens_item on pedidos_cozinha_itens(venda_item_id);
