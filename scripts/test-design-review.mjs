import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';

execFileSync(process.execPath,['scripts/build-preview.mjs'],{stdio:'inherit'});
const source = await readFile('dist-preview/js/preview-design-review.js', 'utf8');
const files = ['tokens','reset','typography','global','layout','sidebar','topbar',
  'buttons','forms','table','badges','sheet','modal','toolbar','kpi','pages',
  'estoque','cardapio','pdv','react-app','compat'];
for (const name of files) await access(join('dist-preview','css', name+'.css'));

function run(query, previouslyEnabled=false) {
  const storage = new Map(previouslyEnabled ? [['stockos-preview-design-review-v1','novo']] : []);
  const links=[];
  const events=[];
  const doc={
    createElement(tag) { return {tag,style:{},dataset:{},setAttribute(){},
      addEventListener(name, fn) {events.push([name,fn]);}}; },
    head:{appendChild(value){links.push(value);}},
    body:{appendChild(){}},
    addEventListener(name,fn) {events.push([name,fn]);}
  };
  const location={href:'http://127.0.0.1:5173/login.html'+query,reload(){}};
  const window={};
  const localStorage={
    getItem(key){return storage.get(key)??null;},
    setItem(key,value){storage.set(key,value);},
    removeItem(key){storage.delete(key);}
  };
  vm.runInNewContext(source,{URL,location,document:doc,localStorage,window});
  return {links,storage,window,events};
}
let result=run('');
assert.equal(result.links.length,files.length,'Default must enable updated CSS');
assert.equal(result.window.StockOsDesignReview.mode,'novo');
result=run('?design=novo');
assert.equal(result.links.length,files.length);
assert.deepEqual(result.links.map(l=>l.href),files.map(n=>'/css/'+n+'.css'));
assert.equal(result.links.at(-1).href,'/css/compat.css');
assert.equal(result.storage.get('stockos-preview-design-review-v1'),'novo');
result=run('',true);
assert.equal(result.window.StockOsDesignReview.mode,'novo');
result=run('?design=original',true);
assert.equal(result.links.length,0);
assert.equal(result.window.StockOsDesignReview.mode,'original');
assert.equal(result.storage.get('stockos-preview-design-review-v1'),'original');
result=run('',false);
assert.equal(result.window.StockOsDesignReview.mode,'novo');
const javaLogin=await readFile('src/main/resources/static/login.html','utf8');
assert.doesNotMatch(javaLogin,/preview-design-review/);
console.log('PASS design preview: updated default, stylesheet order, persistence, revert, Java isolation');
