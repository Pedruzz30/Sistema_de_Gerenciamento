// Confere o código de barras da folha com um leitor de verdade (ZXing), para cada primeiro dígito.
// Precisa de: npm install --no-save @zxing/library@0.21.3 pngjs@7.0.0
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
const require = createRequire(import.meta.url);
const zxing = require('@zxing/library');
globalThis.window = {};
await import(pathToFileURL('src/main/resources/static/js/codigo-barras.js').href);
const B = window.StockOSCodigoBarras;
function ler(codigo) {
  const bits = B.modulosEan13(codigo);
  const escala = 3, margem = 15, largura = (bits.length + margem * 2) * escala, altura = 60;
  const cinza = new Uint8ClampedArray(largura * altura).fill(255);
  for (let x = 0; x < largura; x++) {
    const modulo = Math.floor(x / escala) - margem;
    if (modulo >= 0 && modulo < bits.length && bits[modulo] === '1') for (let y = 0; y < altura; y++) cinza[y * largura + x] = 0;
  }
  const leitor = new zxing.MultiFormatReader();
  leitor.setHints(new Map([[zxing.DecodeHintType.POSSIBLE_FORMATS, [zxing.BarcodeFormat.EAN_13]], [zxing.DecodeHintType.TRY_HARDER, true]]));
  return leitor.decode(new zxing.BinaryBitmap(new zxing.HybridBinarizer(new zxing.RGBLuminanceSource(cinza, largura, altura)))).getText();
}
let ok = 0;
for (const base of ['400638133393', '200000000057', '789100010010', '590123412345', '012345678901', '987654321098', '211111111111', '355555555555', '666666666666', '700000000000', '888888888888', '944444444444']) {
  const codigo = base + B.digitoVerificador(base);
  const lido = ler(codigo).padStart(13, '0');
  if (lido !== codigo) { console.log('ERRO', codigo, lido); process.exit(1); }
  ok++;
}
console.log('PASS: o leitor ZXing leu', ok, 'códigos, um para cada primeiro dígito de 0 a 9 e mais', ok - 10);
