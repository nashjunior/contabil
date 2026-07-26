/**
 * PII (CPF) — invariante: mascarado na exibicao (ADR-0033 item 4, ADR-0013 do backend).
 * O cliente trata CPF como opaco: nunca desmascara, nunca loga em claro. O backend real
 * (ContextoExecucaoHttp) so aceita CPF cru no header X-Govbr-Cpf para montar o contexto de
 * autenticacao da requisicao (nunca para exibir) — a mascara abaixo e so para exibicao na UI.
 */
export function maskCpf(cpfDigits: string): string {
  const digits = cpfDigits.replace(/\D/g, '');
  if (digits.length !== 11) {
    throw new Error('CPF invalido: esperado 11 digitos');
  }
  return `***.${digits.slice(3, 6)}.***-**`;
}
