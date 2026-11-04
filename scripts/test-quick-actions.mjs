import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';

const file = 'src/main/resources/static/js/quick-actions.js';
execFileSync(process.execPath, ['--check', file], {stdio:'inherit'});
const source = await readFile(file, 'utf8');
const permissions = new Set(['VER_ESTOQUE','EDITAR_ESTOQUE','VER_VENDAS']);
const env = {
  window: {
    temPermissao: permission => permissions.has(permission),
    StockosAuth: {isSupabase: () => false}
  },
  document: {readyState:'loading',addEventListener() {}},
  console
};
vm.runInNewContext(source,env,{filename:file});
const actions = env.window.StockOSQuickActions;
assert.ok(actions,'Quick actions should expose the search contract');
assert.equal(actions.search('registrar VENDA').length,1);
assert.equal(actions.search('cadastrar produto').length,1);
assert.equal(actions.search('COTACOES').length,0,'Never display finance-only actions');
assert.equal(actions.search('funcionarios').length,0,'Never display restricted management actions');
assert.equal(actions.search('nota fiscal').length,0,'No purchases permission');
assert.equal(actions.search('buscar produto').length,1);
assert.equal(actions.search('nao existe').length,0);
permissions.delete('EDITAR_ESTOQUE');
assert.equal(actions.search('cadastrar produto').length,0,'Create requires editing permission');
permissions.clear();
assert.equal(actions.search('abrir caixa').length,0,'No sales permission means no cash operations');
assert.equal(actions.search('visao geral').length,1,'Dashboard accessible');
const staging = {
  window: {StockosAuth: {isSupabase: () => true}},
  document: {readyState:'loading',addEventListener() {}},
  console
};
vm.runInNewContext(source,staging,{filename:file});
assert.equal(staging.window.StockOSQuickActions,undefined,'Never expose legacy quick actions in Supabase staging');
const shared = await readFile('src/main/resources/static/js/shared.js','utf8');
assert.ok(shared.includes("script.src = '/js/quick-actions.js'"));
assert.ok(shared.includes("style.href = '/css/quick-actions.css'"));
console.log('PASS: StockOS UX command palette, permissions, search and Supabase isolation');
