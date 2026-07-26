/**
 * O backend real (ExecucaoController) nao tem `@JsonFormat(shape=STRING)` nos campos
 * `valor` (BigDecimal) — o Jackson padrao do Spring Boot serializa isso como NUMERO JSON
 * (ex.: `"valor":1000.00`), nao como string. `JSON.parse` nativo converteria isso para
 * `number` (float) antes de qualquer codigo nosso rodar — exatamente o que o invariante
 * "dinheiro decimal" probe. Esta funcao intercepta o TEXTO cru da resposta antes do
 * parse, colocando aspas nos campos monetarios conhecidos, para que cheguem como string.
 *
 * Dependencia cross-time (ver ADR-0033 e comentario no contrato-provisorio.yaml):
 * pedir ao backend (Aurelio) `@JsonFormat(shape = JsonFormat.Shape.STRING)` em `valor`
 * torna este workaround desnecessario — o parse viraria `JSON.parse` puro.
 */
const DEFAULT_MONEY_FIELDS = ['valor'] as const;

export function parseJsonPreservingMoney(rawText: string, moneyFields: readonly string[] = DEFAULT_MONEY_FIELDS): unknown {
  const pattern = new RegExp(
    `"(${moneyFields.join('|')})":\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)(?=[,}\\s])`,
    'g',
  );
  const withQuotedMoney = rawText.replace(pattern, '"$1":"$2"');
  return JSON.parse(withQuotedMoney);
}

/**
 * Direcao request (cliente -> backend): serializa o corpo normalmente via JSON.stringify,
 * depois "desaspeia" os campos monetarios para virarem literal numerico JSON cru — o formato
 * que `BigDecimal valor` aceita em QUALQUER configuracao padrao do Jackson (nao depende da
 * leniencia de aceitar string entre aspas). Preserva o texto decimal exato digitado pelo
 * usuario (nunca passa por `Number()` nesse caminho).
 */
export function stringifyBodyWithRawMoney(
  body: Record<string, unknown>,
  moneyFields: readonly string[] = DEFAULT_MONEY_FIELDS,
): string {
  const json = JSON.stringify(body);
  const pattern = new RegExp(`"(${moneyFields.join('|')})":"(-?\\d+(?:\\.\\d+)?)"`, 'g');
  return json.replace(pattern, '"$1":$2');
}
