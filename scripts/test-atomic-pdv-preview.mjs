import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

const script=await readFile('preview-source/js/preview-mock.js','utf8');
const store=new Map();
const storage={getItem:key=>store.get(key)??null,setItem:(key,value)=>store.set(key,String(value)),removeItem:key=>store.delete(key)};
const location={href:'https://demo.local/caixas.html',origin:'https://demo.local',pathname:'/caixas.html',reload(){}};
const document={
  createElement:()=>({style:{},textContent:'',setAttribute(){}}),
  addEventListener(){},body:{appendChild(){}}
};
const context={
  window:{fetch:async()=>{throw Error('Real network must not be used by preview');}},
  document,location,localStorage:storage,Response,Request,URL,Date,Map,console
};
vm.createContext(context);
vm.runInContext(script,context,{filename:'preview-mock.js'});
const request=async(path,method='GET',body)=>{
  const response=await context.window.fetch(path,{method,...(body?{body:JSON.stringify(body)}:{})});
  return {status:response.status,data:await response.json()};
};
const products=(await request('/api/produtos')).data;
const before=products.find(p=>p.id===7).quantidadeAtual;
const cash=(await request('/api/caixas')).data.find(c=>c.numeroCaixa===1);
const cashBefore=cash.saldoAtual;
const movementsBefore=(await request('/api/movimentacoes')).data.length;
const cashRowsBefore=(await request('/api/caixas/sessoes/1/movimentacoes')).data.length;
const key='pdv_atomic_test_001';

let response=await request('/api/preview/transacoes/venda','POST',{
  idempotencyKey:key,numeroCaixa:1,itens:[{idProduto:7,quantidade:2}]
});
assert.equal(response.status,201);
assert.equal(response.data.valor,8);
assert.equal((await request('/api/produtos')).data.find(p=>p.id===7).quantidadeAtual,before-2);
assert.equal((await request('/api/caixas')).data.find(c=>c.numeroCaixa===1).saldoAtual,cashBefore+8);
assert.equal((await request('/api/movimentacoes')).data.length,movementsBefore+1);
assert.equal((await request('/api/caixas/sessoes/1/movimentacoes')).data.length,cashRowsBefore+1);

response=await request('/api/preview/transacoes/venda','POST',{
  idempotencyKey:key,numeroCaixa:1,itens:[{idProduto:7,quantidade:2}]
});
assert.equal(response.status,200,'Idempotent retry should not execute twice');
response=await request('/api/preview/transacoes/venda','POST',{
  idempotencyKey:key,numeroCaixa:1,itens:[{idProduto:7,quantidade:3}]
});
assert.equal(response.status,409,'Same key with different cart must be rejected');

const stockBefore=(await request('/api/produtos')).data.find(p=>p.id===7).quantidadeAtual;
const caixaBefore=(await request('/api/caixas')).data.find(c=>c.numeroCaixa===1).saldoAtual;
const stockMovBefore=(await request('/api/movimentacoes')).data.length;
const cashierMovBefore=(await request('/api/caixas/sessoes/1/movimentacoes')).data.length;
response=await request('/api/preview/transacoes/venda','POST',{
  idempotencyKey:'pdv_atomic_failure_002',numeroCaixa:1,
  itens:[{idProduto:7,quantidade:1},{idProduto:17,quantidade:100}]
});
assert.equal(response.status,409,'Overstock sale should fail');
assert.equal((await request('/api/produtos')).data.find(p=>p.id===7).quantidadeAtual,stockBefore,'No partial stock reduction');
assert.equal((await request('/api/caixas')).data.find(c=>c.numeroCaixa===1).saldoAtual,caixaBefore,'No partial cash change');
assert.equal((await request('/api/movimentacoes')).data.length,stockMovBefore,'No partial stock movement');
assert.equal((await request('/api/caixas/sessoes/1/movimentacoes')).data.length,cashierMovBefore,'No partial cash movement');

response=await request('/api/preview/transacoes/venda','POST',{
  idempotencyKey:'pdv_atomic_closed_003',numeroCaixa:2,itens:[{idProduto:7,quantidade:1}]
});
assert.equal(response.status,409,'Closed cash drawer must deny sale');
assert.equal((await request('/api/produtos')).data.find(p=>p.id===7).quantidadeAtual,stockBefore);

console.log('PASS: atomic preview PDV sale, idempotency, insufficient stock, closed cash, and rollback');
