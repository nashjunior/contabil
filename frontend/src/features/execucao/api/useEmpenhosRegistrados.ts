/**
 * Consulta REAL (RAZ-121): GET /execucao/empenhos — lista persistida de empenhos
 * registrados por ente/exercício, não um cache local das respostas de POST desta sessão
 * (gap fechado pela RAZ-199: `ExecucaoConsultaController.empenhos` existia sem consumidor
 * no client). Complementa `useExecucaoOrcamentaria` (agregado do período).
 *
 * Hook fino (ADR-0041): adapta o caso de uso `consultarEmpenhosRegistrados` ao React
 * Query e repassa o `signal` que o próprio framework de dados fornece.
 */
import { useQuery } from '@tanstack/react-query';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { consultarEmpenhosRegistrados } from '../application/consultarEmpenhosRegistrados';

export function empenhosRegistradosKey(enteId: string, exercicio: number) {
  return ['empenhos-registrados', enteId, exercicio] as const;
}

export function useEmpenhosRegistrados(exercicio: number) {
  const contexto = useGovbrContexto();

  return useQuery({
    queryKey: empenhosRegistradosKey(contexto.enteId, exercicio),
    queryFn: ({ signal }) => consultarEmpenhosRegistrados({ exercicio }, contexto, { signal }),
  });
}
