# StockOS Preview — Netlify
A branch de deploy é isolada da base Java. Os arquivos públicos de demonstração ficam em `preview-source/` e não alteram `src/main`.

O `netlify.toml` determina:
- Build: `npm run build:preview`
- Publish: `dist-preview`
- Node: 22

No Netlify `spacestocklab`, a branch de produção configurada é `main`. Após a revisão e merge do PR de deploy para main, o Netlify usará o build isolado e os próximos pushes na main republicarão o preview.

Login e autorização são *simulados*; nunca use dados reais, não compartilhe senhas e não trate o ambiente como sistema operacional.
Nenhuma API, banco de dados ou backend de produção são utilizados. Parte dos módulos oferece somente leitura ou operações simplificadas.
