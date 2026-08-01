import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { LiquidacaoRequest } from '../../../shared/api/client';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { registrarLiquidacao } from '../application/registrarLiquidacao';
import { filaAprovacaoKey } from './useFilaAprovacao';

/**
 * Hook fino (ADR-0041): adapta o caso de uso `registrarLiquidacao` ao React Query. A liquidação
 * nasce sempre `pendente` (ADR-0023) — invalida a fila de pendentes para o item recém-criado
 * aparecer sem precisar de refresh manual (mesmo padrão de invalidação por evento de domínio de
 * `useCriarEmpenho`).
 */
export function useRegistrarLiquidacao() {
  const contexto = useGovbrContexto();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: LiquidacaoRequest) => registrarLiquidacao(body, contexto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: filaAprovacaoKey(contexto.enteId, 'pendente') });
    },
  });
}
