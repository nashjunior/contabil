/**
 * Dinheiro decimal (invariante do backend, ver AGENTS.md do repo). O backend usa
 * BigDecimal (escala 2, HALF_EVEN); o cliente NUNCA converte valor monetario para
 * `number` — nem para aritmetica, nem para exibicao. `Money` e uma string decimal
 * (branded) validada na borda (formulario/parse de resposta) e formatada so na
 * exibicao via `Intl.NumberFormat`.
 */

declare const MoneyBrand: unique symbol;
export type Money = string & { readonly [MoneyBrand]: true };

const DECIMAL_PATTERN = /^-?\d+(?:\.\d{1,2})?$/;

export function isValidMoney(value: string): value is Money {
  return DECIMAL_PATTERN.test(value.trim());
}

/** Valida e normaliza uma string decimal (ex.: entrada de formulario) para `Money` com escala 2. */
export function toMoney(value: string): Money {
  const trimmed = value.trim().replace(',', '.');
  if (!isValidMoney(trimmed)) {
    throw new Error(`Valor monetario invalido: "${value}" (esperado decimal, ex.: "1000.00")`);
  }
  const [intPart, fracPart = ''] = trimmed.split('.');
  return `${intPart}.${fracPart.padEnd(2, '0')}` as Money;
}

const BRL_FORMATTER = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** Formata para exibicao (R$ 1.000,00). Ponto de conversao para number e so aqui, na borda de exibicao. */
export function formatMoneyBRL(value: Money | string): string {
  if (!isValidMoney(value)) {
    throw new Error(`Valor monetario invalido para formatacao: "${value}"`);
  }
  return BRL_FORMATTER.format(Number(value));
}
