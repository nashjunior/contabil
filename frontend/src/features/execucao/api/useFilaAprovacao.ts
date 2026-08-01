/**
 * Hook fino (ADR-0041): adapta o caso de uso `consultarFilaAprovacao` ao React Query
 * e repassa o `signal` que o próprio React Query fornece ao `queryFn`.
 */
import { useQuery } from '@tanstack/react-query';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { consultarFilaAprovacao } from '../application/consultarFilaAprovacao';

export function filaAprovacaoKey(enteId: string, statusAprovacao: 'pendente' | 'aprovada' | 'devolvida') {
  return ['fila-aprovacao', enteId, statusAprovacao] as const;
}

export function useFilaAprovacao(statusAprovacao: 'pendente' | 'aprovada' | 'devolvida' = 'pendente') {
  const contexto = useGovbrContexto();

  return useQuery({
    queryKey: filaAprovacaoKey(contexto.enteId, statusAprovacao),
    queryFn: ({ signal }) => consultarFilaAprovacao({ statusAprovacao }, contexto, { signal }),
  });
}
