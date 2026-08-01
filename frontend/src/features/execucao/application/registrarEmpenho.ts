import { execucaoClient, type EmpenhoRequest, type EmpenhoResponse, type GovbrContexto, type RequestOptions } from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): sem React nem React Query, nomeado para
 * espelhar o use case real do backend (`execucao-application/RegistrarEmpenho.java`).
 * `useCriarEmpenho` é o único adaptador de framework por cima disto — cache/
 * invalidação do React Query ficam no hook, não aqui.
 */
export function registrarEmpenho(
  body: EmpenhoRequest,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<EmpenhoResponse> {
  return execucaoClient.registrarEmpenho(body, contexto, options);
}
