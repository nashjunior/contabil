import {
  execucaoClient,
  type FilaAprovacaoResponse,
  type GovbrContexto,
  type RequestOptions,
} from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): espelha `execucao-application/ConsultarFilaAprovacao.java`
 * no backend. `useFilaAprovacao` adapta isto ao React Query.
 */
export function consultarFilaAprovacao(
  params: {
    statusAprovacao?: 'pendente' | 'aprovada' | 'devolvida';
    cursor?: string;
    limit?: number;
    fonte?: string;
    dataInicio?: string;
    dataFim?: string;
    valorMin?: string;
    valorMax?: string;
  },
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<FilaAprovacaoResponse> {
  return execucaoClient.consultarFilaAprovacao(params, contexto, options);
}
