import { defineConfig } from 'vite';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = dirname(fileURLToPath(import.meta.url));
const frontendRoot = resolve(repoRoot, 'src/main/resources/static');

// The existing StockOS project is a multi-page vanilla HTML application.
// Use the real Spring resource directory as the Vite root, avoiding copies,
// bundler rewrites or changes to the Java deployment artifacts.
const cleanRoutes = new Map([
  ['/', '/login.html'],
  ['/login', '/login.html'],
  ['/dashboard', '/dashboard.html'],
  ['/estoque', '/estoque.html'],
  ['/caixas', '/caixas.html'],
  ['/cardapio', '/cardapio.html'],
  ['/cotacoes', '/cotacoes.html'],
  ['/fornecedores', '/fornecedores.html'],
  ['/funcionarios', '/funcionarios.html'],
  ['/notas', '/notas.html'],
  ['/logs', '/logs.html'],
  ['/saas-comandas', '/saas-comandas.html'],
  ['/saas-admin', '/saas-admin.html'],
]);

export default defineConfig({
  root: frontendRoot,
  appType: 'mpa',
  publicDir: false,
  // This Vite server only serves the frontend in development.
  // Java remains the source of truth for ALL authentication/authorization.
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    open: '/login.html',
    proxy: {
      '^/api(?:/|$)': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false,
      },
    },
  },
  plugins: [{
    name: 'stockos-existing-pages-and-classic-script-reload',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.method !== 'GET') return next();
        const pathname = new URL(req.url ?? '/', 'http://127.0.0.1:5173').pathname;
        const target = cleanRoutes.get(pathname);
        if (!target) return next();
        res.statusCode = 302;
        res.setHeader('Location', target);
        res.end();
      });

      // Most legacy scripts are classic <script> tags, not ES module imports.
      // Force full reload on these edits; Vite HMR handles CSS and modules.
      server.watcher.on('change', (filename) => {
        const pathInFrontend = relative(frontendRoot, filename);
        if (pathInFrontend.startsWith('..' + sep) || pathInFrontend === '..') return;
        if (/\.(?:js|jsx)$/.test(pathInFrontend)) {
          server.ws.send({ type: 'full-reload' });
        }
      });
    },
  }],
});
