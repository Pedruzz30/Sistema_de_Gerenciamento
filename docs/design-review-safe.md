# StockOS — revisão segura do redesign

Esta branch contém os **21 arquivos CSS** do patch local `99bf0f2`, sobre a `main` atual, sem sobrescrever o HTML ou JavaScript do sistema em produção.

## Como testar no VS Code

```powershell
git fetch origin
git switch -c teste-redesign --track origin/feat/stockos-design-review-safe-20261030
npm run dev
```

Abra `http://127.0.0.1:5173/login.html` e clique no botão **Testar visual novo** no canto superior direito.
Também é possível ativar com `http://127.0.0.1:5173/login.html?design=novo`.

Para voltar, clique no mesmo botão ou acesse `?design=original`. A escolha fica no `localStorage` do navegador e funciona ao trocar entre as páginas do preview. O teste utiliza somente dados fictícios.

**Não use senhas reais no preview.** O modo mock é isolado do PostgreSQL e Supabase, e não valida autenticação real.

## Por que não fizemos merge cego?

- O patch move `scripts/build-preview.mjs` para `scripts/build/preview.mjs`, mas o `npm run dev` do PR #14 chama o caminho original. A alteração quebraria o fluxo local sem atualizar dependências.
- Vários workflows também dependem dos caminhos antigos.
- O patch adiciona `vite` e um `package-lock.json` de outra configuração do `package.json` atual. Não foram incorporados por enquanto.
- O CSS existente carregado por padrão nas páginas (`tokens.css` e `app.css` na raiz) não usa automaticamente os novos módulos de `css/`. Eles são carregados **somente no preview e por opção**, para comparação visual segura.
- A documentação atual de Netlify foi atualizada na `main`; não substituímos por cópia antiga do patch.

## Checklist antes de integrar o visual como padrão

1. Executar `npm run test:preview`, `npm run test:design` e `npm run test:local-preview`.
2. Abrir login, dashboard, estoque, cardápio, fornecedores, notas e caixas no modo original e no novo.
3. Testar janelas de 375px, 768px, 1280px e 1920px. Verificar tabelas, menus, modais, filtros, rolagem e contraste.
4. Verificar se o CSS novo não sobrescreve ícones, cabeçalhos ou seletores incompatíveis; ajustar os arquivos sem duplicar CSS.
5. Só após aprovação visual, integrar os estilos gradualmente às páginas reais; o Java Spring deve permanecer com o tema original até esse momento.

## O que não foi alterado

- Backend Java, PostgreSQL, Supabase, migrations, autenticação real.
- Scripts e comandos existentes do projeto.
- Os arquivos HTML/JS das páginas reais.

O build Netlify continua gerando `dist-preview/` a partir do frontend real, com os mocks exclusivos da demonstração. A integração está protegida por um **PR de revisão**; não faça merge até validar o novo tema visualmente.
