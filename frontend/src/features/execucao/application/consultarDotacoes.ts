import {
  execucaoClient,
  type DotacoesResponse,
  type GovbrContexto,
  type RequestOptions,
} from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): espelha `consulta.ExecucaoConsultaController.dotacoes`
 * (RAZ-148/ADR-0038) do backend. `DotacaoPicker` adapta isto a `useAsyncOptions` e repassa
 * o `signal` que o framework de dados fornece.
 */
export function consultarDotacoes(
  params: { exercicio: number; busca?: string; cursor?: string; limit?: number },
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<DotacoesResponse> {
  return execucaoClient.consultarDotacoes(params, contexto, options);
}
