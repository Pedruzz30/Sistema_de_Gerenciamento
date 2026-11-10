# StockOS — demonstração pública isolada

Esta documentação substitui a versão desatualizada da antiga PR #5. O preview atual é um **artefato estático de demonstração**, não o ambiente SaaS comercial.

## Criar e testar

```bash
npm run build:preview
npm run test:preview
npm run dev
```

O build copia o frontend Java original para `dist-preview/`, aplica **adaptações somente na cópia** e injeta `preview-source/js/preview-mock.js`. Não altera o login canônico do Spring Boot. O Netlify usa `netlify.toml`, publica `dist-preview` e executa `npm run build:preview`.

- [Abrir demonstração](https://spacestocklab.netlify.app/dashboard.html)
- [Comandas fictícias](https://spacestocklab.netlify.app/comandas-demo.html)

Na demo, a navegação pode começar sem login (identidade fictícia), inclusive em navegadores que não permitem persistir `localStorage`. O banner deve mostrar **DEMONSTRAÇÃO**. Estoque, PDV, comandas, dashboard e dados de empresas grandes são simulados e podem ter diferentes níveis de cobertura; nenhuma operação está ligada a pagamento, cliente ou banco real.

O login real **não é substituído** pela identidade fictícia. Rotas `/api` são respondidas pelo mock local. Jamais adicionar proxy Netlify para um PostgreSQL, Supabase ou Spring de produção.

## Desenvolvimento com Spring

Use `npm run dev:vite` para frontend local com proxy `/api` para Spring em `127.0.0.1:8080`; veja `docs/stockos-vite-local.md`. O servidor mock `npm run dev` e o Vite com backend são ambientes distintos.

## Regras de revisão e segurança

1. Verifique `npm run test:preview` e checks GitHub antes de mergear alterações de frontend.
2. Teste fluxos móveis e o carregamento da URL principal em um navegador real. CI não comprova comportamento em um aparelho específico.
3. Não inclua dados pessoais, tokens de sessão, segredo Supabase, `service_role` ou credenciais PostgreSQL no pacote estático.
4. Dados de demonstração no navegador não são backup e podem sumir quando o armazenamento local é apagado ou bloqueado.
5. O mock não representa autorização multi-tenant nem transações financeiras homologadas. A integração JWT/tenant está na PR #33, que **não** deve ser promovida enquanto não houver teste staging real.

## Histórico das PRs

A antiga #5 (mock/performance) foi substituída por builds, testes e previews posteriores, incluindo #39–#49; seus arquivos históricos continuam disponíveis no GitHub. A PR #3 inclui arquitetura SaaS experimental distinta da integração #33 e deve ser revisada antes de importar funcionalidades para o backend final — nunca fazer merge integral dessas branches desatualizadas.
