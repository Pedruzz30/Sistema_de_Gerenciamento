import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

const script=await readFile('src/main/resources/static/js/stockos-tenant-console.js','utf8');
const window={};
vm.runInNewContext(script,{window,URL,console});
const {validStores,pathFor,createApi}=window.StockosTenantConsole;
const a='11111111-1111-4111-8111-111111111111';
const b='22222222-2222-4222-8222-222222222222';
const storeA={id:a,nome:'Loja A',papel:'PROPRIETARIO',ruLocal:1};
const storeB={id:b,nome:'Loja B',papel:'OPERADOR',ruLocal:2};
assert.equal(validStores([storeA])[0].id,a);
assert.throws(()=>validStores([{...storeA,id:'invalid'}]));
assert.throws(()=>validStores([{...storeA,papel:'ABSOLUTE_ADMIN'}]));
assert.throws(()=>validStores([storeA,storeA]));
assert.throws(()=>validStores({items:[storeA]}));
assert.throws(()=>pathFor('../escape','estoque/saldos'));
assert.throws(()=>pathFor(a,'caixas'));
assert.equal(pathFor(a,'estoque/saldos'),'/api/v2/estabelecimentos/'+a+'/estoque/saldos');

const calls=[];
let available=[storeA];
const replies={
  '/api/v2/estabelecimentos':()=>available,
  ['/api/v2/estabelecimentos/'+a+'/catalogo/produtos']:()=>[{id:1,nome:'Produto A'}],
  ['/api/v2/estabelecimentos/'+a+'/catalogo/fornecedores']:()=>[],
  ['/api/v2/estabelecimentos/'+a+'/estoque/saldos']:()=>[],
  ['/api/v2/estabelecimentos/'+a+'/estoque/movimentos']:()=>[]
};
const fetcher=async (path,opts)=>{
  calls.push({path,opts});
  const data=replies[path];
  return {ok:!!data,status:data?200:404,json:async()=>data?.()};
};
const auth={getAccessToken:async()=> 'jwt-test-token'};
const api=createApi(fetcher,auth);
assert.equal((await api.stores())[0].id,a);
const data=await api.load(a);
assert.equal(data.products[0].nome,'Produto A');
assert.equal(data.stores.length,1);
assert.equal(calls.length,6);
for(const call of calls){
  assert.equal(call.opts.method,'GET');
  assert.equal(call.opts.headers.Authorization,'Bearer jwt-test-token');
  assert.equal(call.opts.cache,'no-store');
}
const count=calls.length;
await assert.rejects(api.load(b),/não autorizado/i);
assert.equal(calls.length,count+1,'Only discovery may run for unauthorized tenant');
assert.equal(calls.at(-1).path,'/api/v2/estabelecimentos');
assert.ok(!calls.some(c=>c.path.includes(b)));

available=[];
await assert.rejects(api.load(a),/não autorizado/i);
available=[storeA,storeB];
const apiUnauthorized=createApi(async()=>({ok:false,status:403}),auth);
await assert.rejects(apiUnauthorized.stores(),/não autorizado/i);
const noToken=createApi(fetcher,{getAccessToken:async()=>null});
await assert.rejects(noToken.stores(),/Sessão encerrada/);
console.log('PASS: tenant discovery, JWT request, read-only allowlist, revoked and cross-tenant denial');
