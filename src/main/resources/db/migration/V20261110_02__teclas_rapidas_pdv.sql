-- Teclas rápidas do caixa: o que não tem código de barras (gelo, copo, caldo, porção)
-- entra com um toque ou com Alt+1 a Alt+8. Uma variação por tecla.
create table pdv_teclas_rapidas (
    posicao smallint primary key,
    variacao_id bigint not null references variacoes_item_cardapio(id) on delete cascade,
    constraint ck_pdv_teclas_posicao check (posicao between 1 and 8)
);
