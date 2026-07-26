import { useMutation, useQueryClient } from '@tanstack/react-query';
import { execucaoClient, type EmpenhoRequest } from '../../../shared/api/client';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { useAdicionarExecucaoRegistrada } from './useExecucoesRegistradas';
import { execucaoOrcamentariaKey } from './useExecucaoOrcamentaria';

export function useCriarEmpenho() {
  const contexto = useGovbrContexto();
  const queryClient = useQueryClient();
  const adicionarNaLista = useAdicionarExecucaoRegistrada(contexto.enteId);

  return useMutation({
    mutationFn: (body: EmpenhoRequest) => execucaoClient.registrarEmpenho(body, contexto),
    onSuccess: (registro) => {
      adicionarNaLista(registro);
      // Invalida o agregado real (ADR-0033 item 3: invalidação por evento de domínio) —
      // o próximo `useExecucaoOrcamentaria` refaz o GET e reflete o empenho recém-criado.
      queryClient.invalidateQueries({ queryKey: execucaoOrcamentariaKey(contexto.enteId, registro.exercicio) });
    },
  });
}
