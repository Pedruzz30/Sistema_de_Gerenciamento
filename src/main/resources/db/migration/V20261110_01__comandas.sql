-- Comandas: quem consome na loja paga no fim. Cada comanda guarda os itens numa venda
-- em rascunho, finalizada no caixa como as outras. O número do cartão volta a ser
-- usado depois que a comanda fecha, por isso só pode haver uma aberta com cada número.
create table comandas (
    id bigserial primary key,
    numero integer not null,
    venda_id bigint not null references vendas(id),
    status varchar(20) not null,
    aberta_em timestamp not null,
    aberta_por bigint,
    fechada_em timestamp,
    constraint ck_comandas_numero check (numero between 1 and 9999),
    constraint ck_comandas_status check (status in ('ABERTA', 'FECHADA', 'CANCELADA'))
);

create unique index uk_comandas_numero_aberta on comandas(numero) where status = 'ABERTA';
create unique index uk_comandas_venda on comandas(venda_id);
