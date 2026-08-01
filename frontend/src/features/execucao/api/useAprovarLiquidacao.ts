import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError, type AprovacaoRequest } from '../../../shared/api/client';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { aprovarLiquidacao, CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA } from '../application/aprovarLiquidacao';
import { filaAprovacaoKey } from './useFilaAprovacao';

/**
 * Hook fino (ADR-0041): adapta o caso de uso `aprovarLiquidacao` ao React Query. Invalida a
 * fila de pendentes tanto no sucesso (a decisão tira o item de `pendente`) quanto nos erros que
 * significam "o item já não é mais decidível" (ADR-0055, decisão 4) — nesses casos o item
 * também precisa sumir/reaparecer atualizado na fila, mesmo a mutação tendo falhado.
 */
export function useAprovarLiquidacao() {
  const contexto = useGovbrContexto();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ liquidacaoId, body }: { liquidacaoId: string; body: AprovacaoRequest }) =>
      aprovarLiquidacao(liquidacaoId, body, contexto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: filaAprovacaoKey(contexto.enteId, 'pendente') });
    },
    onError: (erro) => {
      if (erro instanceof ApiError && CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA.has(erro.codigo)) {
        queryClient.invalidateQueries({ queryKey: filaAprovacaoKey(contexto.enteId, 'pendente') });
      }
    },
  });
}
