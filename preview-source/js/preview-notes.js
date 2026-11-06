/* Preview-only purchase invoice simulation. No backend, fiscal issuance or payment. */
(() => {
  'use strict';
  const original = window.fetch.bind(window);
  const KEY = 'stockos-preview-notes-'+(window.StockOsPreview?.scenario || 'basic')+'-v1';
  const load = () => { try { const v=JSON.parse(localStorage.getItem(KEY)||'null');return Array.isArray(v)?v:null; } catch {return null;} };
  let notas = load() || (window.StockOsPreview?.getSeedNotes?.() || []);
  const save = () => { try {localStorage.setItem(KEY,JSON.stringify(notas));}catch{} };
  const answer = (data,status=200) => Promise.resolve(new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}}));
  const error = (message,status=400) => answer({erro:message},status);
  const urlFor = input => new URL(input instanceof Request?input.url:String(input),location.href);
  const jsonBody = init => { try{return JSON.parse(init?.body||'{}');}catch{return {};} };
  const now = () => new Date().toISOString();
  const updateTotal = nota => {nota.total=+(nota.itens.reduce((s,i)=>s+i.subtotal,0)).toFixed(2);return nota;};
  // ── Importação de XML (só demonstração; o sistema real faz isso no servidor) ──
  const KEY_VINCULOS='stockos-preview-nfe-vinculos-v1';
  const carregarVinculos=()=>{try{return JSON.parse(localStorage.getItem(KEY_VINCULOS)||'{}');}catch{return {};}};
  const salvarVinculos=v=>{try{localStorage.setItem(KEY_VINCULOS,JSON.stringify(v));}catch{}};
  const tag=(pai,nome)=>pai.getElementsByTagNameNS('*',nome)[0]?.textContent?.trim()||'';
  const num=(pai,nome)=>Number(tag(pai,nome))||0;
  const gtin=valor=>/^\d{8}$|^\d{12,14}$/.test(valor)?valor:null;
  function lerXmlDemo(xml){
    const doc=new DOMParser().parseFromString(String(xml||''),'application/xml');
    const inf=doc.getElementsByTagNameNS('*','infNFe')[0];
    if(doc.querySelector('parsererror')||!inf)throw Error('Este XML não é uma NF-e.');
    const chave=inf.getAttribute('Id')?.replace(/^NFe/,'')||'';
    if(!/^\d{44}$/.test(chave))throw Error('Chave de acesso inválida.');
    const emit=inf.getElementsByTagNameNS('*','emit')[0],dest=inf.getElementsByTagNameNS('*','dest')[0];
    const itens=[...inf.getElementsByTagNameNS('*','det')].map(det=>{
      const prod=det.getElementsByTagNameNS('*','prod')[0];
      const custo=num(prod,'vProd')+num(prod,'vFrete')+num(prod,'vSeg')+num(prod,'vOutro')-num(prod,'vDesc')+num(det,'vIPI')+num(det,'vICMSST');
      return {numero:Number(det.getAttribute('nItem')),codigoFornecedor:tag(prod,'cProd'),descricao:tag(prod,'xProd'),ncm:tag(prod,'NCM'),cest:tag(prod,'CEST'),
        eanComercial:gtin(tag(prod,'cEAN')),eanTributavel:gtin(tag(prod,'cEANTrib')),unidadeComercial:tag(prod,'uCom').toUpperCase(),quantidadeComercial:num(prod,'qCom'),
        unidadeTributavel:tag(prod,'uTrib').toUpperCase(),quantidadeTributavel:num(prod,'qTrib'),custoTotal:+custo.toFixed(2)};
    });
    return {chave,numero:tag(inf,'nNF'),serie:tag(inf,'serie'),dataEmissao:tag(inf,'dhEmi').slice(0,19),cnpj:tag(emit,'CNPJ'),fornecedorNome:tag(emit,'xNome'),
      destinatarioNome:dest?tag(dest,'xNome'):'',itens};
  }
  function fatorDemo(i){
    const razao=i.quantidadeComercial>0?i.quantidadeTributavel/i.quantidadeComercial:0;
    if(i.unidadeComercial!==i.unidadeTributavel&&Number.isInteger(razao)&&razao>=2)return {unidadesPorEmbalagem:razao,conferir:false};
    if(['UN','UND','UNID','PC','GF','LT'].includes(i.unidadeComercial))return {unidadesPorEmbalagem:1,conferir:false};
    const m=i.descricao.toUpperCase().match(/(?:\bC\s*\/\s*|\bCX\s*C?\/?\s*|\bFD\s*C?\/?\s*)(\d{1,3})\b|\b(\d{1,3})\s*X\s*\d/);
    const palpite=m?Number(m[1]||m[2]):1;
    return {unidadesPorEmbalagem:palpite>=2?palpite:1,conferir:true};
  }
  async function previaXmlDemo(xml){
    let nota;
    try{nota=lerXmlDemo(xml);}catch(e){return error(e.message);}
    const fornecedores=await (await original('/api/fornecedores')).json();
    const fornecedor=fornecedores.find(f=>String(f.cnpj||'').replace(/\D/g,'')===nota.cnpj);
    const vinculos=carregarVinculos();
    const produtos=await (await original('/api/produtos')).json();
    const itens=[];
    for(const i of nota.itens){
      const v=vinculos[nota.cnpj+'|'+i.codigoFornecedor];
      const conhecido=v&&produtos.find(p=>p.id===v.produtoId);
      if(conhecido){itens.push({...i,produtoId:conhecido.id,produtoNome:conhecido.nome,origem:'VINCULO',unidadesPorEmbalagem:v.unidades,conferir:false});continue;}
      const porCodigo=produtos.find(p=>p.codigoBarras&&(p.codigoBarras===i.eanTributavel||p.codigoBarras===i.eanComercial));
      itens.push({...i,...fatorDemo(i),produtoId:porCodigo?.id??null,produtoNome:porCodigo?.nome??null,origem:porCodigo?'CODIGO_BARRAS':null});
    }
    const total=+itens.reduce((s,i)=>s+i.custoTotal,0).toFixed(2);
    return answer({chaveAcesso:nota.chave,numero:nota.numero,serie:nota.serie,dataEmissao:nota.dataEmissao,fornecedorCnpj:nota.cnpj,fornecedorNome:nota.fornecedorNome,
      fornecedorId:fornecedor?.id??null,fornecedorInativo:fornecedor?fornecedor.ativo===false:false,destinatarioNome:nota.destinatarioNome,destinatarioDocumento:'',valorTotal:total,
      notaExistenteId:notas.find(n=>n.chaveAcesso===nota.chave)?.id??null,avisos:[],itens});
  }
  async function importarXmlDemo(xml,decisoes){
    let nota;
    try{nota=lerXmlDemo(xml);}catch(e){return error(e.message);}
    if(notas.some(n=>n.chaveAcesso===nota.chave))return error('Esta nota já foi importada.',409);
    const porItem=new Map(decisoes.map(d=>[d.numeroItem,d]));
    if(nota.itens.some(i=>!porItem.has(i.numero)))return error('Decida o que fazer com todos os itens.');
    const postar=(url,dados)=>original(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(dados)});
    const vinculos=carregarVinculos();
    const linhas=[];let criados=0,ignorados=0,unidadesTotal=0;
    for(const i of nota.itens){
      const d=porItem.get(i.numero);
      if(d.acao==='IGNORAR'){ignorados++;continue;}
      const fator=Number(d.unidadesPorEmbalagem)||1,unidades=i.quantidadeComercial*fator;
      if(!Number.isInteger(unidades)||unidades<=0)return error('Item '+i.numero+': não dá unidades inteiras.');
      const custo=+(i.custoTotal/unidades).toFixed(2);
      let id=d.produtoId,nome;
      if(d.acao==='CRIAR'){
        const res=await postar('/api/produtos/revenda',{nome:d.nome,codigoBarras:fator>1?i.eanTributavel:(i.eanComercial||i.eanTributavel),precoVenda:d.precoVenda,precoGelado:d.precoGelado,custoUnitario:custo,quantidadeInicial:0,categoriaEstoque:d.categoriaEstoque,
          fardo:fator>=2&&d.precoFardo?{quantidadeUnidades:fator,preco:d.precoFardo,codigoBarras:i.eanComercial}:null});
        if(!res.ok)return error((await res.json().catch(()=>({}))).erro||'Não foi possível criar o produto.',res.status);
        const criado=await res.json();id=criado.produtoId;nome=criado.nome;criados++;
      } else {
        nome=(await (await original('/api/produtos')).json()).find(p=>p.id===Number(id))?.nome;
        if(!nome)return error('Item '+i.numero+': escolha o produto.');
      }
      const entrada=await postar('/api/movimentacoes',{idProduto:Number(id),quantidade:unidades,tipo:'ENTRADA'});
      if(!entrada.ok)return error('Não foi possível atualizar o estoque fictício.');
      vinculos[nota.cnpj+'|'+i.codigoFornecedor]={produtoId:Number(id),unidades:fator};
      unidadesTotal+=unidades;
      linhas.push({id:linhas.length+1,produtoId:Number(id),produtoNome:nome,quantidade:unidades,precoUnitario:custo,subtotal:i.custoTotal});
    }
    if(!linhas.length)return error('Nenhum item para dar entrada: todos foram ignorados.');
    salvarVinculos(vinculos);
    const fornecedores=await (await original('/api/fornecedores')).json();
    let f=fornecedores.find(x=>String(x.cnpj||'').replace(/\D/g,'')===nota.cnpj);
    const fornecedorCriado=!f;
    if(!f){const novo=await postar('/api/fornecedores',{nome:nota.fornecedorNome,cnpj:nota.cnpj,telefone:''});if(novo.ok)f=await novo.json();}
    const id=Math.max(0,...notas.map(n=>n.id))+1;
    notas.unshift(updateTotal({id,numero:'NF-e '+nota.numero,numeroNfe:nota.numero,chaveAcesso:nota.chave,fornecedorId:f?.id??0,fornecedorNome:nota.fornecedorNome,
      status:'CONFIRMADA',dataEmissao:nota.dataEmissao||now(),itens:linhas,total:0}));
    save();
    return answer({notaId:id,fornecedorCriado,fornecedorNome:nota.fornecedorNome,produtosCriados:criados,itensLancados:linhas.length,itensIgnorados:ignorados,unidades:unidadesTotal,
      custoTotal:+linhas.reduce((s,l)=>s+l.subtotal,0).toFixed(2)});
  }
  /** Nota de exemplo com chave nova a cada clique, para testar sem ter um XML em mãos. */
  function xmlExemplo(){
    const verificador=(base,peso=2)=>{let soma=0;for(let i=base.length-1;i>=0;i--){soma+=Number(base[i])*peso;peso=peso===9?2:peso+1;}const r=soma%11;return r<2?0:11-r;};
    const numero=String(Date.now()).slice(-9);
    const base='35261111222333000181550010'+numero.padStart(17,'0');
    const chave=base+verificador(base);
    const det=(n,cod,ean,xProd,uCom,qCom,eanTrib,uTrib,qTrib,vProd,st)=>'<det nItem="'+n+'"><prod><cProd>'+cod+'</cProd><cEAN>'+ean+'</cEAN><xProd>'+xProd+'</xProd><NCM>22030000</NCM><CFOP>5405</CFOP><uCom>'+uCom+'</uCom><qCom>'+qCom+'</qCom><vProd>'+vProd+'</vProd><cEANTrib>'+eanTrib+'</cEANTrib><uTrib>'+uTrib+'</uTrib><qTrib>'+qTrib+'</qTrib></prod><imposto><ICMS><ICMS10><vICMSST>'+st+'</vICMSST></ICMS10></ICMS></imposto></det>';
    return '<nfeProc xmlns="http://www.portalfiscal.inf.br/nfe"><NFe><infNFe Id="NFe'+chave+'"><ide><mod>55</mod><serie>1</serie><nNF>'+numero+'</nNF><dhEmi>2026-11-05T10:30:00-03:00</dhEmi><tpNF>1</tpNF><tpAmb>1</tpAmb><finNFe>1</finNFe></ide>'
      +'<emit><CNPJ>11222333000181</CNPJ><xNome>Distribuidora Exemplo LTDA</xNome></emit><dest><xNome>Depósito de demonstração</xNome></dest>'
      +det(1,'CERV350','5901234123457','CERVEJA PILSEN LT 350ML','CX','5','4006381333931','UN','60','150.00','15.00')
      +det(2,'REFRI2L','SEM GTIN','REFRIGERANTE COLA 2L FD C/6','FD','4','SEM GTIN','FD','4','168.00','0.00')
      +det(3,'AGUA500','SEM GTIN','AGUA MINERAL 500ML','UN','24','SEM GTIN','UN','24','24.00','0.00')
      +'</infNFe></NFe></nfeProc>';
  }
  function montarExemploXml(){
    const slot=document.getElementById('xml-exemplo-slot');
    if(!slot||slot.childElementCount)return;
    const botao=document.createElement('button');
    botao.type='button';botao.className='btn btn-secondary';botao.textContent='Usar uma nota de exemplo (demonstração)';
    botao.onclick=()=>window.processarTextoNfe&&window.processarTextoNfe(xmlExemplo());
    slot.append(botao);
  }
  window.fetch = async (input,init={}) => {
    const url=urlFor(input); const path=url.pathname;
    const method=String(init.method||(input instanceof Request?input.method:'GET')).toUpperCase();
    if(url.origin!==location.origin || !path.startsWith('/api/notas'))return original(input,init);
    const body=jsonBody(init);
    // Importação de XML na demonstração: lê o arquivo no navegador e usa o estoque fictício.
    if(path==='/api/notas/importacao/previa' && method==='POST')return previaXmlDemo(body.xml);
    if(path==='/api/notas/importacao' && method==='POST')return importarXmlDemo(body.xml,body.itens||[]);
    if(path==='/api/notas'){
      if(method==='GET')return answer(notas);
      if(method==='POST'){
        const resp=await original('/api/fornecedores/ativos');const fs=await resp.json();
        const f=fs.find(x=>x.id===Number(body.fornecedorId));
        if(!f)return error('Selecione um fornecedor ativo.');
        const id=Math.max(0,...notas.map(n=>n.id))+1;
        const nota={id,numero:'DEMO-'+String(id).padStart(4,'0'),fornecedorId:f.id,fornecedorNome:f.nome,status:'PENDENTE',dataEmissao:now(),itens:[],total:0};
        notas.unshift(nota);save();return answer(nota);
      }
    }
    const match=path.match(/^\/api\/notas\/(\d+)(?:\/(itens|confirmar|pagamento|cancelar))?$/);
    if(!match)return error('Rota de nota não disponível no preview.',501);
    const nota=notas.find(n=>n.id===Number(match[1]));
    if(!nota)return error('Nota de demonstração não encontrada.',404);
    const action=match[2];
    if(!action){
      if(method==='GET')return answer(nota);
      if(method==='DELETE'){
        if(nota.status!=='PENDENTE')return error('Somente rascunhos podem ser descartados.');
        notas=notas.filter(n=>n.id!==nota.id);save();return answer({sucesso:true});
      }
    }
    if(action==='itens' && method==='POST'){
      if(nota.status!=='PENDENTE')return error('Esta nota já foi confirmada.');
      const quantity=Number(body.quantidade),price=Number(body.precoUnitario);
      if(!Number.isInteger(quantity)||quantity<=0||!Number.isFinite(price)||price<0)return error('Quantidade ou preço inválidos.');
      let product;
      if(body.produtoId){
        const res=await original('/api/produtos');product=(await res.json()).find(p=>p.id===Number(body.produtoId));
      } else if(body.novoProduto?.nome){
        const res=await original('/api/produtos',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:body.novoProduto.nome,quantidadeInicial:0,quantidadeMinima:body.novoProduto.quantidadeMinima||0,precoUnitario:body.novoProduto.precoUnitarioBase||price,categoriaEstoque:body.novoProduto.categoriaEstoque||null})});
        if(res.ok)product=await res.json();
      }
      if(!product)return error('Produto não encontrado.');
      const row={id:Math.max(0,...nota.itens.map(i=>i.id))+1,produtoId:product.id,produtoNome:product.nome,quantidade:quantity,precoUnitario:price,subtotal:+(quantity*price).toFixed(2)};
      nota.itens.push(row);updateTotal(nota);save();return answer(nota);
    }
    if(action==='confirmar' && method==='POST'){
      if(nota.status!=='PENDENTE'||!nota.itens.length)return error('Adicione itens ao rascunho antes de confirmar.');
      const stock=await original('/api/produtos');const products=await stock.json();
      // Preflight all products before writing to avoid partially confirmed invoices.
      if(nota.itens.some(i=>!products.some(p=>p.id===i.produtoId)))return error('Produto da nota não encontrado.');
      for(const item of nota.itens){
        const r=await original('/api/movimentacoes',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idProduto:item.produtoId,quantidade:item.quantidade,tipo:'ENTRADA'})});
        if(!r.ok)return error('Não foi possível atualizar o estoque fictício.');
      }
      nota.status='CONFIRMADA';save();return answer(nota);
    }
    if(action==='pagamento' && method==='POST'){
      if(nota.status!=='CONFIRMADA')return error('Somente notas confirmadas podem receber pagamento.');
      nota.status='PAGA';save();return answer(nota);
    }
    if(action==='cancelar' && method==='POST'){
      if(nota.status!=='PENDENTE')return error('Esta demonstração cancela somente rascunhos.');
      nota.status='CANCELADA';save();return answer(nota);
    }
    return error('Operação indisponível na demonstração.',501);
  };
  // Keep the fiscal disclaimer on direct loads and SPA transitions.
  function mountNotesPreview() {
    if(!/\/notas(?:\.html)?\/?$/.test(location.pathname))return;
    const content = document.querySelector('.main-body.content');
    montarExemploXml();
    if(!content || content.querySelector('.notes-demo-banner'))return;
    const banner=document.createElement('div');banner.setAttribute('role','note');banner.className='notes-demo-banner';
    banner.style.cssText='padding:10px 15px;margin:8px 16px;border:1px solid #a7bdd8;border-radius:8px;background:#122436;color:#e6f1ff;font:500 12px system-ui';
    banner.textContent='DEMO: notas, confirmação, estoque e pagamentos são apenas simulações locais. Nenhum documento fiscal é emitido.';
    content.prepend(banner);
  }
  window.StockOSPreviewHooks=window.StockOSPreviewHooks||{};
  window.StockOSPreviewHooks.notas=mountNotesPreview;
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',mountNotesPreview);
  else mountNotesPreview();
})();
