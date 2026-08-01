import {
  execucaoClient,
  type ExecucaoOrcamentariaResponse,
  type GovbrContexto,
  type RequestOptions,
} from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): espelha `execucao-application/ConsultarExecucaoOrcamentaria.java`
 * no backend. `useExecucaoOrcamentaria` adapta isto ao React Query e repassa o
 * `signal` que o próprio React Query fornece ao `queryFn`.
 */
export function consultarExecucaoOrcamentaria(
  exercicio: number,
  mes: number,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<ExecucaoOrcamentariaResponse> {
  return execucaoClient.consultarExecucaoOrcamentaria(exercicio, mes, contexto, options);
}
