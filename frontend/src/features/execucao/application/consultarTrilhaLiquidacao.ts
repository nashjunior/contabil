import {
  execucaoClient,
  type GovbrContexto,
  type RequestOptions,
  type TrilhaResponse,
} from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): espelha `execucao-application/ConsultarTrilhaLiquidacao.java`
 * no backend. `useTrilhaLiquidacao` adapta isto ao React Query.
 */
export function consultarTrilhaLiquidacao(
  liquidacaoId: string,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<TrilhaResponse> {
  return execucaoClient.consultarTrilhaLiquidacao(liquidacaoId, contexto, options);
}
