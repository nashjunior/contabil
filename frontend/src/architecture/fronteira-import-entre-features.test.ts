import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, extname, join, relative, resolve, sep } from 'node:path';

/**
 * Guardrail da fronteira de import entre features (ADR-0032) — RAZ-199 (achado do
 * guardião-frontend no scaffold RAZ-120: a fronteira nasceu sem gate assim que a 2ª
 * feature, `razao`, apareceu).
 *
 * A decisão original do ADR-0032 cita `import/no-restricted-paths` (ESLint), mas o
 * linter real deste repo é `oxlint` (sem esse rule set — mesma lacuna já resolvida para
 * a fronteira `application/` sem React, ver `fronteira-application-sem-react.test.ts`).
 * Este teste aplica o mesmo mecanismo (tool-agnóstico, com dentes) às duas regras do
 * ADR-0032: (1) uma feature não importa de dentro de outra feature, só via `index.ts`
 * público; (2) `shared/` não importa de `features/` (a dependência é sempre a mesma
 * direção — features dependem de shared, nunca o inverso).
 */

const srcDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const featuresDir = join(srcDir, 'features');
const sharedDir = join(srcDir, 'shared');

/** Só código de produção: os testes ficam fora do escopo (mesmo recorte do guardrail irmão). */
function ehArquivoDeCodigo(nome: string): boolean {
  if (/\.(test|spec)\.[cm]?[jt]sx?$/.test(nome)) return false;
  return ['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx'].includes(extname(nome));
}

function walk(dir: string, acc: string[]): void {
  let entradas;
  try {
    entradas = readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const entrada of entradas) {
    const caminho = join(dir, entrada.name);
    if (entrada.isDirectory()) walk(caminho, acc);
    else if (entrada.isFile() && ehArquivoDeCodigo(entrada.name)) acc.push(caminho);
  }
}

function arquivosSob(dir: string): string[] {
  const encontrados: string[] = [];
  walk(dir, encontrados);
  return encontrados;
}

/** Extrai os especificadores de módulo de imports/re-exports/dynamic-imports estáticos. */
function especificadoresImportados(fonte: string): string[] {
  const specs: string[] = [];
  const padroes = [
    /\bimport\s+(?:[^'"]*?\s+from\s+)?['"]([^'"]+)['"]/g,
    /\bexport\s+(?:\*|\{[^}]*\})\s+from\s+['"]([^'"]+)['"]/g,
    /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
  ];
  for (const re of padroes) {
    let m: RegExpExecArray | null;
    while ((m = re.exec(fonte)) !== null) specs.push(m[1]);
  }
  return specs;
}

function resolveEspecificador(deArquivo: string, especificador: string): string {
  return resolve(dirname(deArquivo), especificador);
}

function stripExt(caminho: string): string {
  return caminho.replace(/\.(tsx|ts|mts|cts|jsx|js)$/, '');
}

/** Nome da feature dona de `caminho` (`features/<nome>/...`), ou `null` se fora de `features/`. */
function featureDoCaminho(caminho: string, featuresRoot: string): string | null {
  const rel = relative(featuresRoot, caminho);
  if (rel.startsWith('..') || rel === '') return null;
  return rel.split(sep)[0];
}

/** `true` quando `caminhoResolvido` é exatamente a raiz/índice público da feature (import permitido). */
function ehIndicePublicoDaFeature(caminhoResolvido: string, featuresRoot: string, feature: string): boolean {
  const raiz = join(featuresRoot, feature);
  const semExt = stripExt(caminhoResolvido);
  return semExt === raiz || semExt === join(raiz, 'index');
}

/**
 * Retorna o motivo da violação (string) se `especificador`, importado por `deArquivo`,
 * fere a fronteira de feature do ADR-0032; `undefined` se estiver ok.
 */
function violacaoDeFronteira(
  deArquivo: string,
  especificador: string,
  featuresRoot: string,
  sharedRoot: string,
): string | undefined {
  if (!especificador.startsWith('.')) return undefined; // sem path alias neste repo — pacotes externos não se aplicam
  const resolvido = resolveEspecificador(deArquivo, especificador);

  const donoOrigem = featureDoCaminho(deArquivo, featuresRoot);
  const donoDestino = featureDoCaminho(resolvido, featuresRoot);

  if (donoOrigem && donoDestino && donoDestino !== donoOrigem) {
    if (!ehIndicePublicoDaFeature(resolvido, featuresRoot, donoDestino)) {
      return `feature '${donoOrigem}' importa de dentro de '${donoDestino}' sem passar pelo index.ts público`;
    }
  }

  const origemEhShared = !relative(sharedRoot, deArquivo).startsWith('..');
  if (origemEhShared && donoDestino) {
    return `shared/ importa de features/${donoDestino} — proibido (ADR-0032: dependência é sempre features → shared)`;
  }

  return undefined;
}

describe('Fronteira de import entre features (ADR-0032)', () => {
  it('nenhuma feature importa de dentro de outra feature, e shared/ não importa de features/', () => {
    const arquivos = [...arquivosSob(featuresDir), ...arquivosSob(sharedDir)];

    expect(
      arquivos.length,
      `Nenhum arquivo varrido em ${relative(process.cwd(), featuresDir)} / ${relative(process.cwd(), sharedDir)} — ` +
        'resolução de caminho provavelmente quebrada; guardrail seria placebo.',
    ).toBeGreaterThan(0);

    const violacoes: string[] = [];
    for (const arquivo of arquivos) {
      const fonte = readFileSync(arquivo, 'utf8');
      for (const spec of especificadoresImportados(fonte)) {
        const motivo = violacaoDeFronteira(arquivo, spec, featuresDir, sharedDir);
        if (motivo) {
          violacoes.push(`${relative(process.cwd(), arquivo)} importa '${spec}': ${motivo}`);
        }
      }
    }

    expect(
      violacoes,
      `Fronteira de feature violada (ADR-0032). Violações:\n${violacoes.join('\n')}`,
    ).toEqual([]);
  });

  it('o detector pega import cruzado e shared→features, e permite index.ts público (prova de dentes)', () => {
    const featuresRoot = '/repo/src/features';
    const sharedRoot = '/repo/src/shared';

    // Feature 'execucao' alcançando dentro de 'razao' — violação.
    expect(
      violacaoDeFronteira(
        `${featuresRoot}/execucao/components/Foo.tsx`,
        '../../razao/components/BalanceteTable',
        featuresRoot,
        sharedRoot,
      ),
    ).toMatch(/razao/);

    // Mesma feature, subpasta interna — permitido.
    expect(
      violacaoDeFronteira(
        `${featuresRoot}/execucao/components/Foo.tsx`,
        '../api/useCriarEmpenho',
        featuresRoot,
        sharedRoot,
      ),
    ).toBeUndefined();

    // Via index.ts público da outra feature — permitido (a costura documentada no ADR-0032).
    expect(
      violacaoDeFronteira(`${featuresRoot}/execucao/components/Foo.tsx`, '../../razao', featuresRoot, sharedRoot),
    ).toBeUndefined();
    expect(
      violacaoDeFronteira(`${featuresRoot}/execucao/components/Foo.tsx`, '../../razao/index', featuresRoot, sharedRoot),
    ).toBeUndefined();

    // shared/ alcançando features/ — violação, mesmo via index público.
    expect(
      violacaoDeFronteira(`${sharedRoot}/lib/foo.ts`, '../../features/execucao', featuresRoot, sharedRoot),
    ).toMatch(/shared\/ importa/);

    // Import de pacote externo (sem ponto) — nunca é violação deste guardrail.
    expect(violacaoDeFronteira(`${featuresRoot}/execucao/components/Foo.tsx`, 'react', featuresRoot, sharedRoot)).toBeUndefined();
  });
});
