/**
 * Domínio do agregado Pagamento no front — framework-free, espelhando `Pagamento.java`/
 * `NaturezaPagamento.java`/`Beneficiario.java` (RAZ-230, mesmo padrão de `domain/empenho.ts`).
 * `LiquidacaoId` vem de `domain/liquidacao.ts` (não redeclarado aqui): pagamento sempre
 * referencia uma liquidação já existente, mesma identidade, mesma regra de validação — dois
 * VOs com o mesmo brand e regex duplicados divergiriam silenciosamente com o tempo.
 */
export { LiquidacaoId } from './liquidacao';

export const TIPOS_NATUREZA_PAGAMENTO = ['orcamentario', 'folha_consolidada'] as const;
export type TipoNaturezaPagamento = (typeof TIPOS_NATUREZA_PAGAMENTO)[number];

export function isNaturezaPagamentoValida(valor: string): valor is TipoNaturezaPagamento {
  return (TIPOS_NATUREZA_PAGAMENTO as readonly string[]).includes(valor);
}

/** Texto obrigatório (histórico, nome/CPF-CNPJ do beneficiário, ordem bancária). */
export function isTextoObrigatorioValido(valor: string): boolean {
  return valor.trim().length > 0;
}
