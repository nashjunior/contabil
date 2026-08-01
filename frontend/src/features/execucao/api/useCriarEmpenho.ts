import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { EmpenhoRequest } from '../../../shared/api/client';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { registrarEmpenho } from '../application/registrarEmpenho';
import { empenhosRegistradosKey } from './useEmpenhosRegistrados';
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

  return useMutation({
    mutationFn: (body: EmpenhoRequest) => registrarEmpenho(body, contexto),
    onSuccess: (registro) => {
      // Invalida o agregado e a lista reais (ADR-0033 item 3: invalidação por evento de
      // domínio) — os próximos `useExecucaoOrcamentaria`/`useEmpenhosRegistrados` refazem
      // o GET e refletem o empenho recém-criado (RAZ-121/RAZ-199: lista real, não cache
      // de sessão).
      queryClient.invalidateQueries({ queryKey: execucaoOrcamentariaKey(contexto.enteId, registro.exercicio) });
      queryClient.invalidateQueries({ queryKey: empenhosRegistradosKey(contexto.enteId, registro.exercicio) });
    },
  });
}
