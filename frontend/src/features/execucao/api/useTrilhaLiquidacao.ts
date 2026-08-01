/**
 * Hook fino (ADR-0041): adapta o caso de uso `consultarTrilhaLiquidacao` ao React Query
 * e repassa o `signal` que o próprio React Query fornece ao `queryFn`.
 */
import { useQuery } from '@tanstack/react-query';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { consultarTrilhaLiquidacao } from '../application/consultarTrilhaLiquidacao';

export function trilhaLiquidacaoKey(enteId: string, liquidacaoId: string) {
  return ['trilha-liquidacao', enteId, liquidacaoId] as const;
}

export function useTrilhaLiquidacao(liquidacaoId: string) {
  const contexto = useGovbrContexto();

  return useQuery({
    queryKey: trilhaLiquidacaoKey(contexto.enteId, liquidacaoId),
    queryFn: ({ signal }) => consultarTrilhaLiquidacao(liquidacaoId, contexto, { signal }),
  });
}
