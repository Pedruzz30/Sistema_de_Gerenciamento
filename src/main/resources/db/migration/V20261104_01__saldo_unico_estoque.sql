-- Saldo único de estoque.
--
-- Até aqui existiam dois saldos para o mesmo produto:
--   * produtos.quantidade_atual (inteiro, legado): cadastro, entrada/saída
--     manual, notas fiscais e tela de Estoque;
--   * estoques_produto.quantidade_atual (decimal): PDV, compras e estornos.
-- A partir desta versão o saldo oficial é o decimal (filial 1) e a coluna
-- legada passa a ser só um espelho (SincronizadorEstoque).

-- 1) Produtos controlados por estoque que ainda não têm saldo decimal na
--    filial padrão recebem o saldo legado. O custo médio inicial vem do
--    último preço de nota fiscal confirmada/paga do produto (ou 0).
insert into estoques_produto (
    filial_id, produto_id, quantidade_atual, quantidade_reservada,
    custo_medio, version, atualizado_em
)
select
    1,
    p.id,
    greatest(p.quantidade_atual, 0),
    0,
    coalesce((
        select i.preco_unitario_nota
        from nota_fiscal_itens i
        join notas_fiscais n on n.id = i.nota_fiscal_id
        where i.produto_id = p.id
          and n.status in ('CONFIRMADA', 'PAGA')
        order by coalesce(n.data_confirmacao, n.data_emissao) desc, i.id desc
        limit 1
    ), 0),
    0,
    current_timestamp
from produtos p
where coalesce(p.controlado_por_estoque, true) = true
  and not exists (
      select 1
      from estoques_produto e
      where e.filial_id = 1
        and e.produto_id = p.id
  );

-- 2) Onde já existia saldo decimal, ele é o oficial: o espelho legado é
--    recalculado (arredondado para baixo e nunca negativo).
update produtos p
set quantidade_atual = greatest(floor(e.quantidade_atual), 0)::integer
from estoques_produto e
where e.filial_id = 1
  and e.produto_id = p.id
  and p.quantidade_atual <> greatest(floor(e.quantidade_atual), 0)::integer;
