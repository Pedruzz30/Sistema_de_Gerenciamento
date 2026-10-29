import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const saved = new Map();
const ls = {getItem:k=>saved.get(k)||null,setItem:(k,v)=>saved.set(k,String(v)),removeItem:k=>saved.delete(k)};
const products=[{id:7,nome:'Água mineral 500ml',controladoPorEstoque:true,quantidadeAtual:5,nivelEstoque:'ADEQUADO'}];
const nativeFetch=async url=>new Response(JSON.stringify(url==='/api/produtos'?products:[]),{status:200,headers:{'Content-Type':'application/json'}});
const window={fetch:nativeFetch};
const sandbox={window,localStorage:ls,location:{href:'https://example.test/cardapio.html',origin:'https://example.test'},URL,Response,Request,JSON,console};
vm.runInNewContext(await readFile('preview-source/js/preview-cardapio.js','utf8'),sandbox);
async function call(path,method='GET',body){
  const r=await window.fetch(path,{method,headers:{'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
  return {status:r.status,data:await r.json()};
}
let r=await call('/api/cardapio/admin/categorias');assert.equal(r.data.length,2);
r=await call('/api/cardapio/admin/categorias','POST',{codigo:'TESTE',nomeExibicao:'Novos testes',ordemExibicao:3});assert.equal(r.status,201);
r=await call('/api/cardapio/admin/itens','POST',{nome:'Item demo',codigo:'ITEMDEMO',categoriaId:3,precoVenda:12,tipoItem:'PREPARADO_SOB_DEMANDA',ordemExibicao:1});assert.equal(r.status,201);const itemId=r.data.id;
r=await call('/api/cardapio/admin/itens/'+itemId);assert.equal(r.status,400); // unsupported route methods fail closed
r=await call('/api/cardapio/admin/itens/'+itemId+'/disponibilidade','PATCH',{disponivelParaVenda:false});assert.equal(r.data.disponivelParaVenda,false);
r=await call('/api/cardapio/itens/'+itemId+'/variacoes','POST',{nome:'Pequeno',precoVenda:12,padrao:true,ativa:true});assert.equal(r.status,201);const vId=r.data.id;
r=await call('/api/cardapio/grupos-modificadores','POST',{nome:'Adicional teste',tipoSelecao:'UNICA',quantidadeMaxima:1,ativo:true});assert.equal(r.status,201);const gId=r.data.id;
r=await call('/api/cardapio/grupos-modificadores/'+gId+'/opcoes','POST',{nome:'Molho',acrescimoPreco:2,ativa:true});assert.equal(r.status,201);
r=await call('/api/cardapio/variacoes/'+vId+'/grupos-modificadores','POST',{grupoId:gId,ativo:true});assert.equal(r.status,201);
r=await call('/api/cardapio/variacoes/'+vId+'/configuracao');assert.equal(r.status,200);assert.equal(r.data.grupos.length,1);
assert.ok(saved.has('stockos-preview-cardapio-v2'));
console.log('PASS cardápio mock: categorias, itens, variações, modificadores, isolamento');
