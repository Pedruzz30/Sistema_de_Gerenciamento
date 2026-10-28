# StockOS — preview isolado
Este ambiente **não utiliza** Spring Boot, PostgreSQL, Supabase nem autenticação real.
Ele funciona com fixtures locais em `src/main/resources/static/js/preview-mock.js` e dados guardados no `localStorage` do navegador.

## Gerar o preview estático
```sh
npm run build:preview
```
Publicar o diretório `dist-preview` no Netlify (ou usar a configuração `netlify.preview.toml`). Para um site Netlify conectado ao GitHub, configure build `npm run build:preview` e publicação `dist-preview`; após merge na branch configurada, os deploys futuros serão automáticos.

O preview injeta o mock somente nos HTMLs copiados: **não modifica o HTML original usado pelo Spring Boot**.
Login aceita qualquer nome e senha somente no modo demo; não existe identidade autenticada.
Dashboard e estoque respondem a operações demonstrativas; demais módulos exibem dados vazios ou recusam operações ainda não simuladas (HTTP 501). Não usar para clientes reais.
Dados demo são totalmente fictícios; podem ser redefinidos no console com `StockOsPreview.reset()`.

## Backend real
Mantenha `npm run dev` + Spring Boot para desenvolvimento integrado. Nunca configure o Netlify preview para encaminhar /api ao banco real.

## Desempenho
O dashboard agora busca suas cinco APIs em paralelo e atualiza cartões/seções à medida que as respostas chegam.
Isso melhora a latência percebida, mas **não substitui** medição no servidor; verifique no DevTools Network os endpoints mais lentos no ambiente real.
