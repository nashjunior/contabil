import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Papel } from './jwt';

const __dirname = dirname(fileURLToPath(import.meta.url));

export type UsuarioRuntime = {
  label: string;
  cpf: string;
  cpfMascarado: string;
  enteId: string;
  papeis: Papel[];
  jwt: string;
};

export type Runtime = {
  backendBaseUrl: string;
  dotacaoIdPorEnte: Record<string, string>;
  usuarios: Record<
    'enteALancador' | 'enteAAutorizador' | 'enteBLancador' | 'enteBAutorizador',
    UsuarioRuntime
  >;
};

/** Escrito por `global-setup.ts` — só existe depois que backend+Postgres estão de
 * pé e semeados; specs leem uma vez por arquivo (não muda durante a suíte). */
export function carregarRuntime(): Runtime {
  return JSON.parse(readFileSync(join(__dirname, '..', '.runtime.json'), 'utf8')) as Runtime;
}
