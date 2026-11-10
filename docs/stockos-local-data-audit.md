# Auditoria de dados do PostgreSQL local — StockOS

Esta auditoria é **somente leitura**. O arquivo `scripts/dev/auditar-base-local.sql` usa `BEGIN TRANSACTION READ ONLY` e consultas `SELECT`. Não executa exclusões, alterações de esquema ou migrations.

## Banco local correto

O `compose.yaml` atual cria `stockos_dev` (usuário `stockos_local`) e expõe PostgreSQL **somente em `127.0.0.1`**, na porta **${STOCKOS_DB_PORT:-5433}**. Se alterou essa variável, use a sua porta.

No DBeaver, configure PostgreSQL com host `127.0.0.1`, porta `5433` (padrão), database `stockos_dev` e usuário `stockos_local`. Use a senha configurada por você em `STOCKOS_DB_PASSWORD`, sem incluí-la nos arquivos versionados.

Antes da auditoria, confira `docker compose ps` e confirme o nome do banco. **Não execute contra um banco de cliente, Supabase nem staging.**

Abra o arquivo SQL no DBeaver conectado à base local e execute-o. Ele apresenta:

- Quantidade de produtos, fornecedores, caixas, movimentações de estoque e caixa, usuários.
- Produtos com nomes duplicados (normalizados).
- Níveis de estoque por categoria.
- Movimentações de caixa do tipo `VENDA` agrupadas por dia.

## Antes de qualquer limpeza real

A auditoria não oferece limpeza automática. Nunca use `TRUNCATE CASCADE` nem `DELETE` indiscriminado: notas, vendas, cotações e movimentações podem depender de produtos e caixas.

Se for necessário remover dados fictícios, primeiro faça um backup **do banco local correto**, valide sua restauração em outra base descartável e prepare uma migration/ação específica revisada pelo outro desenvolvedor. Nenhuma dessas ações está incluída nesta PR.

A consulta não fornece confirmação de segurança da operação multi-tenant. O isolamento real, privilégios SQL e migrations continuam sob revisão na PR #33.
