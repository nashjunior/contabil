import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { EmpenhoRequest } from '../../../shared/api/client';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { registrarEmpenho } from '../application/registrarEmpenho';
import { useAdicionarExecucaoRegistrada } from './useExecucoesRegistradas';
import { execucaoOrcamentariaKey } from './useExecucaoOrcamentaria';

/**
 * Hook fino (ADR-0041): adapta o caso de uso `registrarEmpenho` ao React
 * Query. Mutations não recebem `signal` automático do React Query v5 — se
 * este envio precisar ser cancelável, quem chama gerencia seu próprio
 * `AbortController` explicitamente (não presumido aqui).
 */
export function useCriarEmpenho() {
  const contexto = useGovbrContexto();
  const queryClient = useQueryClient();
  const adicionarNaLista = useAdicionarExecucaoRegistrada(contexto.enteId);

  return useMutation({
    mutationFn: (body: EmpenhoRequest) => registrarEmpenho(body, contexto),
    onSuccess: (registro) => {
      adicionarNaLista(registro);
      // Invalida o agregado real (ADR-0033 item 3: invalidação por evento de domínio) —
      // o próximo `useExecucaoOrcamentaria` refaz o GET e reflete o empenho recém-criado.
      queryClient.invalidateQueries({ queryKey: execucaoOrcamentariaKey(contexto.enteId, registro.exercicio) });
    },
  });
}
