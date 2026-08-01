import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, extname, join, relative } from 'node:path';

/**
 * Guardrail da fronteira `application/` — ADR-0041 §4/§5 (RAZ-137).
 *
 * Um caso de uso puro em `features/<feature>/application/` NUNCA pode importar
 * React nem a camada de framework de dados: a lógica de negócio da tela tem de
 * ser testável e reutilizável sem montar árvore React. Até aqui a fronteira era
 * só convenção + revisão humana, porque `oxlint` (o linter real deste repo) não
 * expõe um equivalente a `no-restricted-imports` (reconfirmado em oxlint 1.71 —
 * `npx oxlint --rules` não lista nada em "import"). Este teste impõe a fronteira
 * com dentes, tool-agnóstico, espelhando o ArchUnit do backend
 * (`guardrails:architecture-tests`, ex.: `registro_usa_clock_injetado`).
 */

const IMPORTS_PROIBIDOS = ['react', 'react-dom', '@tanstack/react-query'] as const;

const featuresDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'features');

/** Só código de produção: os testes de caso de uso ficam fora do escopo. */
function ehArquivoDeCodigo(nome: string): boolean {
  if (/\.(test|spec)\.[cm]?[jt]sx?$/.test(nome)) return false;
  return ['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx'].includes(extname(nome));
}

/** Lista todo arquivo de código sob qualquer `features/<feature>/application/**`. */
function arquivosApplication(): string[] {
  const encontrados: string[] = [];
  let features: string[];
  try {
    features = readdirSync(featuresDir, { withFileTypes: true })
      .filter((d) => d.isDirectory())
      .map((d) => d.name);
  } catch {
    return encontrados;
  }
  for (const feature of features) {
    const appDir = join(featuresDir, feature, 'application');
    walk(appDir, encontrados);
  }
  return encontrados;
}

function walk(dir: string, acc: string[]): void {
  let entradas;
  try {
    entradas = readdirSync(dir, { withFileTypes: true });
  } catch {
    return; // feature sem camada application/ — ignora
  }
  for (const entrada of entradas) {
    const caminho = join(dir, entrada.name);
    if (entrada.isDirectory()) walk(caminho, acc);
    else if (entrada.isFile() && ehArquivoDeCodigo(entrada.name)) acc.push(caminho);
  }
}

/** Extrai os especificadores de módulo de imports/re-exports/dynamic-imports estáticos. */
function especificadoresImportados(fonte: string): string[] {
  const specs: string[] = [];
  const padroes = [
    /\bimport\s+(?:[^'"]*?\s+from\s+)?['"]([^'"]+)['"]/g, // import x from 'm' | import 'm'
    /\bexport\s+(?:\*|\{[^}]*\})\s+from\s+['"]([^'"]+)['"]/g, // export ... from 'm'
    /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g, // import('m')
  ];
  for (const re of padroes) {
    let m: RegExpExecArray | null;
    while ((m = re.exec(fonte)) !== null) specs.push(m[1]);
  }
  return specs;
}

/** Um specifier viola se for exatamente um módulo proibido ou um subpath dele. */
function moduloProibido(spec: string): string | undefined {
  return IMPORTS_PROIBIDOS.find((m) => spec === m || spec.startsWith(`${m}/`));
}

describe('Fronteira application/ sem React (ADR-0041)', () => {
  it('nenhum caso de uso em features/*/application/ importa React ou React Query', () => {
    const arquivos = arquivosApplication();

    // Dentes: se a resolução do diretório quebrar (ex.: refactor de layout),
    // o teste passaria vazio e viraria placebo. Falha alto se não varreu nada.
    expect(
      arquivos.length,
      `Nenhum arquivo varrido em ${relative(process.cwd(), featuresDir)}/*/application — ` +
        'resolução de caminho provavelmente quebrada; guardrail seria placebo.',
    ).toBeGreaterThan(0);

    const violacoes: string[] = [];
    for (const arquivo of arquivos) {
      const fonte = readFileSync(arquivo, 'utf8');
      for (const spec of especificadoresImportados(fonte)) {
        const proibido = moduloProibido(spec);
        if (proibido) {
          violacoes.push(`${relative(process.cwd(), arquivo)} importa '${spec}' (proibido: ${proibido})`);
        }
      }
    }

    expect(
      violacoes,
      `Casos de uso em application/ devem ser puros (ADR-0041 §4). Violações:\n${violacoes.join('\n')}`,
    ).toEqual([]);
  });

  it('o detector pega imports proibidos e ignora lookalikes (prova de dentes)', () => {
    const pega = (fonte: string) =>
      especificadoresImportados(fonte)
        .map(moduloProibido)
        .filter((m): m is string => Boolean(m));

    expect(pega(`import { useState } from 'react';`)).toEqual(['react']);
    expect(pega(`import { jsx } from 'react/jsx-runtime';`)).toEqual(['react']);
    expect(pega(`import { createRoot } from 'react-dom/client';`)).toEqual(['react-dom']);
    expect(pega(`export { useQuery } from '@tanstack/react-query';`)).toEqual(['@tanstack/react-query']);
    expect(pega(`const q = await import('@tanstack/react-query');`)).toEqual(['@tanstack/react-query']);

    // Lookalikes legítimos que NÃO podem ser bloqueados por prefixo frouxo.
    expect(pega(`import { Link } from 'react-router-dom';`)).toEqual([]);
    expect(pega(`import { useForm } from 'react-hook-form';`)).toEqual([]);
    expect(pega(`import { execucaoClient } from '../../../shared/api/client';`)).toEqual([]);
  });
});
