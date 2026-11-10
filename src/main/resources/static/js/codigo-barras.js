/* Código de barras EAN-13 em SVG, para imprimir. Sem biblioteca: a codificação é a do padrão GS1. */
(() => {
'use strict';

const L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011'];
const G = ['0100111', '0110011', '0011011', '0100001', '0011101', '0111001', '0000101', '0010001', '0001001', '0010111'];
const R = ['1110010', '1100110', '1101100', '1000010', '1011100', '1001110', '1010000', '1000100', '1001000', '1110100'];
// O primeiro dígito não vira barra: ele escolhe se cada um dos 6 seguintes usa a tabela L ou G.
const PARIDADE = ['LLLLLL', 'LLGLGG', 'LLGGLG', 'LLGGGL', 'LGLLGG', 'LGGLLG', 'LGGGLL', 'LGLGLG', 'LGLGGL', 'LGGLGL'];

function digitoVerificador(semDigito) {
  let soma = 0;
  let peso = 3;
  for (let i = semDigito.length - 1; i >= 0; i--) {
    soma += Number(semDigito[i]) * peso;
    peso = peso === 3 ? 1 : 3;
  }
  return (10 - (soma % 10)) % 10;
}

function ehEan13(codigo) {
  return /^\d{13}$/.test(String(codigo || '')) && digitoVerificador(codigo.slice(0, 12)) === Number(codigo[12]);
}

/** Os 95 módulos do EAN-13: 1 = barra, 0 = espaço. */
function modulosEan13(codigo) {
  if (!ehEan13(codigo)) throw new Error(`Código EAN-13 inválido: ${codigo}`);
  const paridade = PARIDADE[Number(codigo[0])];
  let bits = '101';
  for (let i = 1; i <= 6; i++) bits += (paridade[i - 1] === 'L' ? L : G)[Number(codigo[i])];
  bits += '01010';
  for (let i = 7; i <= 12; i++) bits += R[Number(codigo[i])];
  return bits + '101';
}

/**
 * SVG pronto para impressão. As margens brancas (11 e 7 módulos) fazem parte do padrão:
 * sem elas, o leitor não acha onde o código começa.
 */
function svgEan13(codigo) {
  const bits = modulosEan13(codigo);
  const margemEsquerda = 11;
  const largura = margemEsquerda + 95 + 7;
  const altura = 50;
  const guardas = new Set([0, 1, 2, 45, 46, 47, 48, 49, 92, 93, 94]);
  let barras = '';
  for (let i = 0; i < bits.length; i++) {
    if (bits[i] !== '1') continue;
    const alto = guardas.has(i) ? altura + 5 : altura;
    barras += `<rect x="${margemEsquerda + i}" y="0" width="1" height="${alto}"/>`;
  }
  const texto = `<text x="4" y="${altura + 9}" font-size="9">${codigo[0]}</text>`
    + `<text x="${margemEsquerda + 3 + 21}" y="${altura + 9}" font-size="9" text-anchor="middle">${codigo.slice(1, 7)}</text>`
    + `<text x="${margemEsquerda + 50 + 21}" y="${altura + 9}" font-size="9" text-anchor="middle">${codigo.slice(7)}</text>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${largura} ${altura + 11}" class="codigo-barras" `
    + `role="img" aria-label="Código de barras ${codigo}" shape-rendering="crispEdges">`
    + `<rect width="${largura}" height="${altura + 11}" fill="#fff"/>`
    + `<g fill="#000">${barras}</g><g fill="#000" font-family="monospace" letter-spacing="1">${texto}</g></svg>`;
}

window.StockOSCodigoBarras = { ehEan13, modulosEan13, svgEan13, digitoVerificador };
})();
