import {
  execucaoClient,
  type EmpenhosRegistradosResponse,
  type GovbrContexto,
  type RequestOptions,
} from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): espelha `consulta.ExecucaoConsultaController.empenhos`
 * (RAZ-121) do backend. `useEmpenhosRegistrados` adapta isto ao React Query e repassa o
 * `signal` que o próprio framework de dados fornece.
 */
export function consultarEmpenhosRegistrados(
  params: { exercicio?: number; dataInicio?: string; dataFim?: string; cursor?: string; limit?: number },
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<EmpenhosRegistradosResponse> {
  return execucaoClient.consultarEmpenhosRegistrados(params, contexto, options);
}
