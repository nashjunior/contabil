/**
 * PII (CPF) — invariante: mascarado na exibicao (ADR-0033 item 4, ADR-0013 do backend).
 * O cliente trata CPF como opaco: nunca desmascara, nunca loga em claro. O backend real
 * (`SessaoAutenticadaHttpResolver`) nunca recebe um CPF cru do cliente — o CPF vem de
 * dentro da assercao gov.br verificada (`Authorization: Bearer` ou cookie de sessao do
 * BFF de login, ADR-0035), nunca de um header dedicado. A mascara abaixo e so para
 * exibicao na UI.
 */
export function maskCpf(cpfDigits: string): string {
  const digits = cpfDigits.replace(/\D/g, '');
  if (digits.length !== 11) {
    throw new Error('CPF invalido: esperado 11 digitos');
  }
  return `***.${digits.slice(3, 6)}.***-**`;
}
