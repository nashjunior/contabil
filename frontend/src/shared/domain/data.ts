/**
 * Validação de data ISO (`aaaa-mm-dd`) — mesma checagem de round-trip contra os componentes
 * originais que `features/execucao/domain/empenho.ts#isDataFatoValida` já usava (RAZ-202):
 * `Date.parse` sozinho não rejeita overflow de calendário (`2026-02-30` viraria `2026-03-02`).
 * Promovida para `shared/domain` (RAZ-230) porque liquidação (data de competência + data de
 * emissão de cada documento de suporte) e pagamento (data de competência) precisam da mesma
 * regra em 3 pontos — `empenho.ts` mantém sua própria cópia (arquivo já testado, sem motivo
 * para tocar) para não misturar o escopo desta issue com um refactor não pedido.
 */
const DATA_ISO_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

export function isDataIsoValida(valor: string): boolean {
  const match = DATA_ISO_PATTERN.exec(valor.trim());
  if (!match) return false;
  const [, anoTexto, mesTexto, diaTexto] = match;
  const ano = Number(anoTexto);
  const mes = Number(mesTexto);
  const dia = Number(diaTexto);
  const data = new Date(Date.UTC(ano, mes - 1, dia));
  return data.getUTCFullYear() === ano && data.getUTCMonth() === mes - 1 && data.getUTCDate() === dia;
}
