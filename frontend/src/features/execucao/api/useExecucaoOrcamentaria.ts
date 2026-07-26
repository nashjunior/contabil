/**
 * Consulta REAL (RAZ-101/ADR-0029): GET /execucao/orcamentaria — agregado do período,
 * não um endpoint inventado. Prova fim-a-fim contra um GET real, não só cache local
 * de POST (complementa `useExecucoesRegistradas`).
 */
import { useQuery } from '@tanstack/react-query';
import { execucaoClient } from '../../../shared/api/client';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';

export function execucaoOrcamentariaKey(enteId: string, exercicio: number, mes?: number) {
  return mes === undefined
    ? (['execucao-orcamentaria', enteId, exercicio] as const)
    : (['execucao-orcamentaria', enteId, exercicio, mes] as const);
}

export function useExecucaoOrcamentaria(exercicio: number, mes: number) {
  const contexto = useGovbrContexto();

  return useQuery({
    queryKey: execucaoOrcamentariaKey(contexto.enteId, exercicio, mes),
    queryFn: () => execucaoClient.consultarExecucaoOrcamentaria(exercicio, mes, contexto),
  });
}
