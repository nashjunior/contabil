/**
 * Consulta REAL (RAZ-101/ADR-0029): GET /execucao/orcamentaria — agregado do período,
 * não um endpoint inventado. Prova fim-a-fim contra um GET real, não só cache local
 * de POST (complementa `useEmpenhosRegistrados`).
 *
 * Hook fino (ADR-0041): adapta o caso de uso `consultarExecucaoOrcamentaria`
 * ao React Query e repassa o `signal` que o próprio React Query fornece ao
 * `queryFn` — trocar de página/período antes da resposta cancela o fetch.
 */
import { useQuery } from '@tanstack/react-query';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { consultarExecucaoOrcamentaria } from '../application/consultarExecucaoOrcamentaria';

export function execucaoOrcamentariaKey(enteId: string, exercicio: number, mes?: number) {
  return mes === undefined
    ? (['execucao-orcamentaria', enteId, exercicio] as const)
    : (['execucao-orcamentaria', enteId, exercicio, mes] as const);
}

export function useExecucaoOrcamentaria(exercicio: number, mes: number) {
  const contexto = useGovbrContexto();

  return useQuery({
    queryKey: execucaoOrcamentariaKey(contexto.enteId, exercicio, mes),
    queryFn: ({ signal }) => consultarExecucaoOrcamentaria(exercicio, mes, contexto, { signal }),
  });
}
