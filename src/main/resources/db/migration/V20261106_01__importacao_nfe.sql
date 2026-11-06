-- Importação do XML da NF-e do fornecedor.

-- A chave de acesso impede dar entrada duas vezes na mesma nota.
alter table notas_fiscais
    add column chave_acesso varchar(44);

alter table notas_fiscais
    add column numero_nfe varchar(20);

create unique index uk_notas_fiscais_chave_acesso
    on notas_fiscais(chave_acesso)
    where chave_acesso is not null;

-- Dados fiscais do produto, que a NFC-e vai exigir.
alter table produtos
    add column ncm varchar(8);

alter table produtos
    add column cest varchar(7);

-- "Amarração": o código do produto na nota daquele fornecedor leva ao nosso
-- produto e guarda quantas unidades vêm em cada embalagem comprada. Da segunda
-- nota em diante o item é reconhecido sozinho.
create table vinculos_produto_fornecedor (
    id bigserial primary key,
    fornecedor_id bigint not null references fornecedores(id),
    codigo_produto varchar(60) not null,
    produto_id integer not null references produtos(id),
    unidades_por_embalagem integer not null,
    atualizado_em timestamp not null,
    constraint uk_vinculos_fornecedor_codigo unique (fornecedor_id, codigo_produto),
    constraint ck_vinculos_unidades check (unidades_por_embalagem >= 1)
);
