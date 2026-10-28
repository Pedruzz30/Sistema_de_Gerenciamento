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


## Publicação automática no Netlify — spacestocklab

O projeto `spacestocklab` está atualmente ligado ao repositório `Pedruzz30/Sistema_de_Gerenciamento` com deploy de `main`. O deploy antigo publicou arquivos-fonte e **não** o preview compilado. Não confundir deploy `ready` com funcionamento das APIs.

1. Revise e integre o PR do preview na `main` **apenas após CI e validação**. Este PR inclui a base de alterações anteriores, portanto revise o diff.
2. No Netlify, abra **Project configuration → Build & deploy → Continuous deployment** e confirme o repositório e **Production branch = main**.
3. Em **Build settings** deixe **Base directory** vazio, **Build command = `npm run build:preview`** e **Publish directory = `dist-preview`**. O `netlify.toml` na raiz também define estes valores e prevalece sobre a interface em caso de divergência.
4. Em **Deploys** execute **Trigger deploy → Deploy site** após o merge. Confirme que o deploy mostra o novo SHA e que foram processadas regras de _redirects/_headers.
5. Teste `https://spacestocklab.netlify.app/login.html`, faça login fictício (qualquer nome/senha), abra dashboard, estoque e fornecedores; confirme a etiqueta DEMONSTRAÇÃO.

Até fazer merge e publicar o commit correto, o endereço no Netlify continuará na versão antiga.

**Segurança**: demo não é autenticação, e os dados no localStorage não são compartilhados entre usuários. Não usar para vendas reais, dados pessoais ou backup. O backend e o Supabase não participam do preview.
