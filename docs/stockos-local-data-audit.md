# Auditoria segura dos dados locais — StockOS

O banco local não foi alterado. Não há exclusão automática de registros.

1. No DBeaver, conecte-se ao PostgreSQL de desenvolvimento (`localhost:5432/sistema_gerenciamento`, salvo se `DB_URL` tiver override).
2. Execute `scripts/dev/auditar-base-local.sql` e revise totais, duplicações e histórico por dia. O script usa transação somente de leitura.
3. Antes de qualquer limpeza, faça backup do banco correto e teste restauração em banco descartável:

```powershell
New-Item -ItemType Directory -Force .\backups-local | Out-Null
pg_dump -h 127.0.0.1 -p 5432 -U postgres -d sistema_gerenciamento -F c -f .\backups-local\stockos-antes-limpeza.dump
```

A senha deve ser digitada quando solicitada. Não versione o backup.

Os produtos podem estar vinculados a notas fiscais, compras, estoque, cotacoes e cardapio. **Não executar TRUNCATE CASCADE ou DELETE irrestrito.** Após auditar, classifique os registros e prepare limpeza específica somente com autorização explícita. Para demonstrações, use outro banco descartável, isolado do Supabase/staging e da base principal.

O dashboard corrigido exibe vendas e ticket médio do dia pelo servidor Java; dados históricos permanecem consultáveis. A página de movimentos mostra apenas os lançamentos do dia na visão geral.
