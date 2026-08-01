import {
  razaoClient,
  type ContasResponse,
  type GovbrContexto,
  type RequestOptions,
} from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): espelha `CatalogoContasController.consultarContas` do
 * backend. `useContas`/`ContaPicker` adaptam isto ao React Query/`useAsyncOptions` e
 * repassam o `signal` fornecido pelo respectivo framework de dados.
 */
export function consultarContas(
  params: { busca?: string; cursor?: string; limit?: number },
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<ContasResponse> {
  return razaoClient.consultarContas(params, contexto, options);
}
