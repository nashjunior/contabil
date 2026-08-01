/**
 * Domínio do agregado Empenho no front — framework-free (zero import de React/RHF/zod),
 * espelhando os value objects e invariantes de `execucao-domain`/`RegistrarEmpenho.java`
 * (RAZ-202/ADR-0043). É a fonte única de verdade das regras; `empenhoSchema.ts` só as
 * deriva para o resolver do formulário, e `application/registrarEmpenho.ts` as consome
 * na fronteira com o caso de uso — nenhuma das duas camadas reimplementa a regra aqui.
 */
import { criarTipoIdVO } from '../../../shared/domain/uuidVO';

export const DotacaoId = criarTipoIdVO<'DotacaoId'>('ID da dotação');
export const CredorId = criarTipoIdVO<'CredorId'>('ID do credor');
export const UnidadeGestoraId = criarTipoIdVO<'UnidadeGestoraId'>('ID da unidade gestora');
export const ContratoId = criarTipoIdVO<'ContratoId'>('ID do contrato');

export const TIPOS_EMPENHO = ['ordinario', 'estimativo', 'global'] as const;
export type TipoEmpenho = (typeof TIPOS_EMPENHO)[number];

export function isTipoEmpenhoValido(valor: string): valor is TipoEmpenho {
  return (TIPOS_EMPENHO as readonly string[]).includes(valor);
}

/** Ano com 4 dígitos — o mesmo formato aceito pelo `exercicio: integer` do `EmpenhoRequest`. */
export function isExercicioValido(valor: string): boolean {
  return /^\d{4}$/.test(valor.trim());
}

/**
 * Data ISO (`aaaa-mm-dd`) e calendário válido — o mesmo formato de `dataFato: date`.
 * `Date.parse` sozinho não rejeita overflow (`2026-02-30` viraria `2026-03-02`), então o
 * round-trip contra os componentes originais é quem garante que o calendário é real.
 */
export function isDataFatoValida(valor: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor.trim());
  if (!match) return false;
  const [, anoTexto, mesTexto, diaTexto] = match;
  const ano = Number(anoTexto);
  const mes = Number(mesTexto);
  const dia = Number(diaTexto);
  const data = new Date(Date.UTC(ano, mes - 1, dia));
  return data.getUTCFullYear() === ano && data.getUTCMonth() === mes - 1 && data.getUTCDate() === dia;
}

/** Texto obrigatório (classificação orçamentária, fonte de recurso, histórico). */
export function isTextoObrigatorioValido(valor: string): boolean {
  return valor.trim().length > 0;
}
