# StockOS — desenvolvimento local com atualização automática

## Uso recomendado (sem backend Java)

1. Abra a raiz do projeto `Sistema_de_Gerenciamento` no VS Code.
2. Execute `npm run dev` **uma vez por sessão**.
3. Abra **http://127.0.0.1:5173/login.html** no navegador.
4. Preencha os campos de login com textos fictícios e entre: a autenticação é simulada.
5. Modifique e salve arquivos em `src/main/resources/static/` (HTML, CSS e JS). O navegador recarrega automaticamente após cada build local; não precisa de commit, push, Netlify, Maven ou Java.
6. Para modificar os dados fictícios/fluxos mock, edite `preview-source/js/preview-*.js`.
7. Encerre o servidor com **Ctrl+C** no terminal.

O servidor usa a mesma construção isolada do Netlify e gera `dist-preview/`; essa pasta é **gerada** e está no .gitignore. Não edite `dist-preview/` diretamente, porque ela é recriada ao salvar arquivos.

### Prefiro a extensão Live Server

Se quiser utilizar especificamente o **Live Server**:

1. Na raiz do projeto, execute `npm run dev:live-server`. Esse comando recompila `dist-preview/` automaticamente quando você salva algo; mantenha-o aberto.
2. No VS Code, **File → Open Folder** e selecione a pasta `dist-preview` (numa segunda janela do VS Code).
3. Abra `login.html` e use **Go Live**. O Live Server precisa servir **`dist-preview` como raiz**, para que `/api/auth/login` seja interceptado pelo mock e o redirect para `/dashboard.html` encontre a página.
4. Você deve **editar os arquivos originais na primeira janela**, não os gerados na janela Live Server. A extensão atualiza a página quando o watcher recria a pasta.

**Atenção:** abrir `src/main/resources/static/login.html` via Live Server não faz login, porque o Live Server não executa Spring Boot. O modo `npm run dev` é mais simples e recomendado para a rotina de desenvolvimento.

### Produção e dados reais

O login e todas as operações de `dist-preview` são **fictícios**, gravados no `localStorage` do navegador. Não forneça credenciais reais. Para validar autenticação, persistência PostgreSQL, regras do servidor e APIs de produção, inicie o backend Spring Boot separadamente. O servidor preview liga apenas a interface mock, não substitui o backend.

Se a porta 5173 estiver ocupada por Vite, use PowerShell:
```powershell
$env:PORT=5174; npm run dev
```
Abra então `http://127.0.0.1:5174/login.html`.

A branch do GitHub e o Netlify continuam usando `npm run build:preview` para publicação; o servidor local não é incluído no deploy.
