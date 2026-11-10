# StockOS — desenvolvimento local com Vite

Esta configuração é **somente para desenvolvimento local**. Vite serve as páginas HTML/CSS/JS já existentes; **Java Spring Boot e PostgreSQL continuam necessários para qualquer função real**.

## Como iniciar

1. Inicie o backend Java do StockOS e o banco PostgreSQL local isolado. Confirme que a API está em `http://127.0.0.1:8080`.
2. Abra outro terminal na raiz do repositório e execute:

```powershell
npm install
npm run dev:vite
```

3. Abra `http://127.0.0.1:5173/login.html`. Também há rotas limpas, como `/estoque` e `/caixas`.

O Vite escuta **apenas em 127.0.0.1:5173**. Rotas `/api/**` são enviadas ao Spring em **127.0.0.1:8080**. CSS e HTML são atualizados no navegador e arquivos JavaScript clássicos acionam recarregamento total. Não expor esse servidor na internet nem usar `--host 0.0.0.0`.

## O que permanece separado

- `npm run dev`: demonstração/mock local, sem autenticação real.
- `npm run dev:vite`: frontend real com proxy; depende do Spring e autenticação do backend.
- `npm run build:preview`: build estático do Netlify, sem Java, banco, pagamentos e credenciais reais.

**Nunca insira senhas de banco, service_role do Supabase ou JWT fixos no frontend.** A configuração não ativa o SaaS, não aplica migrations e não altera as restrições de segurança multi-tenant.

Se a interface abrir, mas chamadas de API falharem, verifique se o Spring está executando em 8080. Falhas de login não são resolvidas pelo Vite.
