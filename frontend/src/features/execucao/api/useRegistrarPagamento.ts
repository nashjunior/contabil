import { useMutation } from '@tanstack/react-query';
import type { PagamentoRequest } from '../../../shared/api/client';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { registrarPagamento } from '../application/registrarPagamento';

/**
 * Hook fino (ADR-0041): adapta o caso de uso `registrarPagamento` ao React Query. Sem
 * invalidação de cache — pagamento não muda `statusAprovacao` de nenhuma liquidação (é um
 * estágio à parte que só consome saldo, ADR-0023) e a UI ainda não tem nenhuma lista de
 * pagamentos/saldo residual por liquidação que precise refletir o POST.
 */
export function useRegistrarPagamento() {
  const contexto = useGovbrContexto();

  return useMutation({
    mutationFn: (body: PagamentoRequest) => registrarPagamento(body, contexto),
  });
}
