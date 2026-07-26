// Gate de build (ADR-0031 item 4): falha se algum par fg/bg declarado em
// color.tokens.json ($extensions["contabil.contrastPairs"]) ficar abaixo do
// ratio WCAG AA minimo (4.5:1 texto normal, 3:1 texto grande/UI).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const tokensPath = path.join(__dirname, '..', 'tokens', 'color.tokens.json');
const raw = JSON.parse(readFileSync(tokensPath, 'utf-8'));

function resolveRef(tokens, ref) {
  const path = ref.replace(/^\{|\}$/g, '').split('.');
  let node = tokens;
  for (const segment of path) {
    node = node?.[segment];
  }
  if (!node) throw new Error(`Referencia de token nao encontrada: ${ref}`);
  return typeof node.$value === 'string' && node.$value.startsWith('{')
    ? resolveRef(tokens, node.$value)
    : node.$value;
}

function hexToRgb(hex) {
  const clean = hex.replace('#', '');
  const bigint = parseInt(clean, 16);
  return {
    r: (bigint >> 16) & 255,
    g: (bigint >> 8) & 255,
    b: bigint & 255,
  };
}

function relativeLuminance({ r, g, b }) {
  const channel = (c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function contrastRatio(hexA, hexB) {
  const lumA = relativeLuminance(hexToRgb(hexA));
  const lumB = relativeLuminance(hexToRgb(hexB));
  const lighter = Math.max(lumA, lumB);
  const darker = Math.min(lumA, lumB);
  return (lighter + 0.05) / (darker + 0.05);
}

const pairs = raw.color.$extensions['contabil.contrastPairs'];
let failed = false;

for (const pair of pairs) {
  const fgHex = resolveRef(raw, pair.fg);
  const bgHex = resolveRef(raw, pair.bg);
  const ratio = contrastRatio(fgHex, bgHex);
  const pass = ratio >= pair.minRatio;
  const status = pass ? 'OK ' : 'FALHA';
  console.log(`[contrast] ${status} ${pair.context}: ${pair.fg} on ${pair.bg} = ${ratio.toFixed(2)}:1 (min ${pair.minRatio}:1)`);
  if (!pass) failed = true;
}

if (failed) {
  console.error('\n[contrast] Gate de build falhou: par(es) de cor abaixo do WCAG AA minimo.');
  process.exit(1);
}
console.log('\n[contrast] Todos os pares passam WCAG AA.');
