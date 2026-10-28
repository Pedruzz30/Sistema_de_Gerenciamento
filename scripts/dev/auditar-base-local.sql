-- StockOS: auditoria SOMENTE LEITURA do PostgreSQL LOCAL.
-- Nunca execute no Supabase, producao ou banco de clientes.
BEGIN TRANSACTION READ ONLY;
SELECT current_database() AS banco, current_user AS usuario, inet_server_addr() AS servidor, inet_server_port() AS porta;
SELECT 'produtos' AS tabela, count(*) AS registros FROM public.produtos
UNION ALL SELECT 'fornecedores', count(*) FROM public.fornecedores
UNION ALL SELECT 'caixas', count(*) FROM public.caixas
UNION ALL SELECT 'movimentacoes_caixa', count(*) FROM public.movimentacoes_caixa
UNION ALL SELECT 'movimentacoes_estoque', count(*) FROM public.movimentacoes_estoque
UNION ALL SELECT 'usuarios', count(*) FROM public.usuarios;
SELECT lower(trim(nome)) AS nome_normalizado, count(*) AS ocorrencias
FROM public.produtos GROUP BY lower(trim(nome)) HAVING count(*) > 1
ORDER BY ocorrencias DESC LIMIT 30;
SELECT coalesce(categoria_estoque, 'SEM_CATEGORIA') AS categoria,
count(*) AS produtos, count(*) FILTER (WHERE quantidade_atual = 0) AS zerados
FROM public.produtos GROUP BY categoria_estoque ORDER BY produtos DESC;
SELECT data_hora::date AS dia, count(*) AS transacoes, sum(valor) AS vendas
FROM public.movimentacoes_caixa WHERE tipo = 'VENDA'
GROUP BY data_hora::date ORDER BY dia DESC LIMIT 30;
COMMIT;
