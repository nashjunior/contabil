import {
  razaoClient,
  type BalanceteResponse,
  type GovbrContexto,
  type RequestOptions,
} from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): espelha `RazaoConsultaController.balancete` do backend.
 * `useBalancete` adapta isto ao React Query e repassa o `signal` que o próprio
 * React Query fornece ao `queryFn`.
 */
export function consultarBalancete(
  exercicio: number,
  mes: number,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<BalanceteResponse> {
  return razaoClient.consultarBalancete(exercicio, mes, contexto, options);
}
