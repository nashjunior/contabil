/** GET /transparencia/{enteId}/despesas/totalizacoes — Tela 1b (Totais). */
import { useQuery } from '@tanstack/react-query';
import type { FiltroDespesasPublicas } from '../../../shared/api/transparenciaClient';
import { totalizarDespesasPublicas } from '../application/totalizarDespesasPublicas';

export function totalizacoesPublicasKey(enteId: string, filtro: FiltroDespesasPublicas) {
  return ['transparencia-totalizacoes', enteId, filtro] as const;
}

export function useTotalizacoesPublicas(enteId: string, filtro: FiltroDespesasPublicas, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: totalizacoesPublicasKey(enteId, filtro),
    queryFn: ({ signal }) => totalizarDespesasPublicas(enteId, filtro, { signal }),
    enabled: options?.enabled ?? true,
  });
}
