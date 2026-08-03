/**
 * GET /transparencia/{enteId}/despesas — Tela 1 (Lista). `useInfiniteQuery`, não `useQuery`:
 * a paginação é keyset (`cursor`/`proximoCursor`/`haMais`) e a tela usa "Carregar mais"
 * (acumula páginas), não paginação numerada substituindo o conteúdo (UX-4/ADR-0060 decisão 5).
 */
import { useInfiniteQuery } from '@tanstack/react-query';
import type { FiltroDespesasPublicas } from '../../../shared/api/transparenciaClient';
import { consultarDespesasPublicas } from '../application/consultarDespesasPublicas';

export function despesasPublicasKey(enteId: string, filtro: FiltroDespesasPublicas) {
  return ['transparencia-despesas', enteId, filtro] as const;
}

export function useDespesasPublicas(enteId: string, filtro: FiltroDespesasPublicas) {
  return useInfiniteQuery({
    queryKey: despesasPublicasKey(enteId, filtro),
    queryFn: ({ pageParam, signal }) =>
      consultarDespesasPublicas(enteId, { ...filtro, cursor: pageParam ?? undefined }, { signal }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (ultimaPagina) => (ultimaPagina.haMais ? (ultimaPagina.proximoCursor ?? undefined) : undefined),
  });
}
