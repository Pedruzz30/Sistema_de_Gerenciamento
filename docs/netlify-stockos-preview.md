# StockOS Preview — Netlify

O frontend canônico fica em `src/main/resources/static/`. O build publicado copia as páginas HTML, CSS e JavaScript desse diretório, injeta as simulações isoladas de `preview-source/js/preview-*.js` e escreve `dist-preview/`. A árvore `src/main/` da aplicação Java nunca recebe os mocks.

O `netlify.toml` configura:
- Build: `npm run build:preview`
- Publish: `dist-preview`
- Node: 22

O site Netlify `spacestocklab` acompanha a branch `main`. A demonstração não usa API Java real, PostgreSQL, Supabase ou autenticação real: o login e as operações são simulados localmente no navegador com dados fictícios.

## Trabalhar sem commit e push

Consulte [Desenvolvimento local](desenvolvimento-local.md): `npm run dev` fornece a mesma demonstração na porta 5173, com reconstrução automática a cada alteração dos arquivos do frontend. Nenhum push é necessário para visualizar uma mudança local. Para publicar no Netlify, faça commit e push normalmente.

**Atenção:** o preview é apenas uma demonstração de interface e não substitui os testes de integração do backend.
