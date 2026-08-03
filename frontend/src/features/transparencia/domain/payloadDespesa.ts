/**
 * Leitura tolerante do `payload` sanitizado de `ItemTransparencia` (`JsonNode` livre,
 * `PayloadDespesaPublica` no client). GAP REAL verificado no código do produtor
 * (`PublicadorTransparenciaExecucao.payloadEmpenho/payloadLiquidacao/payloadPagamento`,
 * comentado na issue RAZ-290): o shape do payload varia por `estagio` —
 * - `empenhado`: tem `numeroSequencial`, `dataFato`, `credorId`, `unidadeGestoraId`,
 *   `contratoId` (pode ser `null`), `historico`.
 * - `liquidado`: só `dataCompetencia`, `empenhoId`, `historico`, `documentosSuporte[]` — SEM
 *   `credorId`/`orgaoId`/`numeroSequencial`.
 * - `pago`: só `dataCompetencia`, `liquidacaoId`, `historico`, `natureza`, `beneficiario`
 *   (`{nome, documento}` ou `null`) — SEM `credorId`/`orgaoId`/`numeroSequencial`.
 * Não é lacuna desta tela: é a Nota de Empenho sendo a única publicação com identificação de
 * credor/órgão/número hoje. As telas usam estes helpers em vez de acessar `payload.credorId`
 * direto, para nunca fabricar um valor e sempre tratar a ausência como "não disponível para
 * este estágio", não como bug de parse.
 */
import type { PayloadDespesaPublica } from '../../../shared/api/transparenciaClient';

function texto(payload: PayloadDespesaPublica, chave: string): string | null {
  const valor = payload[chave];
  return typeof valor === 'string' && valor.length > 0 ? valor : null;
}

export function estagioDoPayload(payload: PayloadDespesaPublica): string {
  return texto(payload, 'estagio') ?? 'desconhecido';
}

/** `dataFato` (empenho) ou `dataCompetencia` (liquidação/pagamento) — mesmo fallback que
 * `TransparenciaPublicaController.csv#primeiroTexto` já aplica no backend. */
export function dataDoPayload(payload: PayloadDespesaPublica): string | null {
  return texto(payload, 'dataFato') ?? texto(payload, 'dataCompetencia');
}

export function valorDoPayload(payload: PayloadDespesaPublica): string | null {
  return texto(payload, 'valor');
}

/** Só populado para `estagio="empenhado"` — `null` em liquidação/pagamento (gap real, não
 * fabricado: o payload dessas duas simplesmente não carrega o número do empenho). */
export function numeroEmpenhoDoPayload(payload: PayloadDespesaPublica): string | null {
  return texto(payload, 'numeroSequencial');
}

/** Só populado para `estagio="empenhado"`. */
export function credorIdDoPayload(payload: PayloadDespesaPublica): string | null {
  return texto(payload, 'credorId');
}

/** `orgaoId` (nome usado pelo filtro/CSV) é `unidadeGestoraId` no payload real — mesmo
 * fallback do backend. Só populado para `estagio="empenhado"`. */
export function orgaoIdDoPayload(payload: PayloadDespesaPublica): string | null {
  return texto(payload, 'orgaoId') ?? texto(payload, 'unidadeGestoraId');
}

export function contratoIdDoPayload(payload: PayloadDespesaPublica): string | null {
  return texto(payload, 'contratoId');
}

export function historicoDoPayload(payload: PayloadDespesaPublica): string | null {
  return texto(payload, 'historico');
}

export type BeneficiarioPayload = { nome: string | null; documento: string | null };

/** Só presente em `estagio="pago"` — `null` quando o pagamento é folha consolidada
 * (`NaturezaPagamento.FOLHA_CONSOLIDADA`, sem beneficiário individual) ou em qualquer outro
 * estágio (o payload nem tem a chave). */
export function beneficiarioDoPayload(payload: PayloadDespesaPublica): BeneficiarioPayload | null {
  const bruto = payload['beneficiario'];
  if (!bruto || typeof bruto !== 'object') return null;
  const registro = bruto as Record<string, unknown>;
  return {
    nome: typeof registro['nome'] === 'string' ? registro['nome'] : null,
    documento: typeof registro['documento'] === 'string' ? registro['documento'] : null,
  };
}
