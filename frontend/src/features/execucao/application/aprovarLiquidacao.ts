import {
  ApiError,
  execucaoClient,
  type AprovacaoRequest,
  type GovbrContexto,
  type LiquidacaoResponse,
  type RequestOptions,
} from '../../../shared/api/client';
import type { CamposDecisaoAprovacao } from '../domain/decisaoAprovacaoSchema';

/**
 * Caso de uso puro (ADR-0041): sem React nem React Query, nomeado para espelhar o use case real
 * do backend (`AprovarPagamento.java`). `useAprovarLiquidacao` é o único adaptador de framework
 * por cima disto.
 */
export function aprovarLiquidacao(
  liquidacaoId: string,
  body: AprovacaoRequest,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<LiquidacaoResponse> {
  return execucaoClient.aprovarLiquidacao(liquidacaoId, body, contexto, options);
}

/** Mapeia os campos já validados por `decisaoAprovacaoSchema` (ADR-0055, decisão 3) para o
 * input deste caso de uso — motivo só viaja quando a decisão é devolver. */
export function paraAprovacaoRequest(campos: CamposDecisaoAprovacao): AprovacaoRequest {
  return {
    decisao: campos.decisao,
    motivo: campos.decisao === 'devolver' ? campos.motivo.trim() : undefined,
  };
}

/**
 * Códigos de erro (ADR-0055, decisão 4) cujo efeito é o item ter deixado de ser decidível —
 * outra decisão já venceu a corrida, ou o registro sumiu entre o carregamento da fila e o
 * clique. Nesses casos a fila precisa ser reconsultada (o item some da lista de pendentes) e o
 * modal fecha; nos demais códigos o modal segue aberto com o erro inline.
 */
export const CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA: ReadonlySet<string> = new Set([
  'liquidacao_ja_decidida',
  'liquidacao_nao_encontrada',
  'empenho_nao_encontrado',
]);

const MENSAGEM_POR_CODIGO: Record<string, string> = {
  auto_aprovacao_vedada:
    'Você não pode decidir sobre uma liquidação que você mesmo registrou (ou cujo empenho é seu) — segregação de funções.',
  liquidacao_ja_decidida: 'Esta liquidação já foi decidida por outro usuário enquanto você revisava.',
  sem_permissao: 'Seu perfil não tem permissão para aprovar/devolver liquidações (papel AUTORIZADOR necessário).',
  mfa_requerido: 'Esta ação exige verificação adicional de segurança. Saia e entre novamente para concluir a verificação.',
  motivo_devolucao_obrigatorio: 'Informe o motivo da devolução.',
  liquidacao_nao_encontrada: 'Esta liquidação não está mais disponível. A fila foi atualizada.',
  empenho_nao_encontrado: 'Esta liquidação não está mais disponível. A fila foi atualizada.',
};

/** Mapeamento de erro → mensagem amigável (ADR-0055, decisão 4): `erro.codigo` primeiro,
 * fallback pro `erro.message` cru do backend, fallback final pra erro sem contrato (rede etc). */
export function mensagemAmigavelDecisao(erro: unknown): string {
  if (erro instanceof ApiError) {
    return MENSAGEM_POR_CODIGO[erro.codigo] ?? erro.message;
  }
  return 'Não foi possível concluir a decisão. Tente novamente.';
}
