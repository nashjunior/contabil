/**
 * Cache de sessao (React Query) dos empenhos que ESTE cliente registrou de fato via
 * API real nesta sessao do navegador — dado real (resposta do POST), nao inventado.
 * Complementa (nao substitui) `useExecucaoOrcamentaria`, que consulta o agregado real
 * via GET /execucao/orcamentaria — este hook e so a lista "o que eu acabei de criar",
 * util para feedback imediato do formulario mesmo quando o agregado ainda nao refletiu.
 */
import { useQueryClient, useQuery } from '@tanstack/react-query';
import type { EmpenhoResponse } from '../../../shared/api/client';

export function empenhosSessaoKey(enteId: string) {
  return ['empenhos-sessao', enteId] as const;
}

export function useExecucoesRegistradas(enteId: string) {
  return useQuery({
    queryKey: empenhosSessaoKey(enteId),
    queryFn: () => [] as EmpenhoResponse[],
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

export function useAdicionarExecucaoRegistrada(enteId: string) {
  const queryClient = useQueryClient();
  return (registro: EmpenhoResponse) => {
    queryClient.setQueryData<EmpenhoResponse[]>(empenhosSessaoKey(enteId), (atual = []) => [registro, ...atual]);
  };
}
