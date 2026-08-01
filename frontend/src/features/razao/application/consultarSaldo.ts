import {
  razaoClient,
  type SaldoContaResponse,
  type GovbrContexto,
  type RequestOptions,
} from '../../../shared/api/client';

/**
 * Caso de uso puro (ADR-0041): espelha `RazaoConsultaController.saldo` do backend.
 * `useSaldoConta` adapta isto ao React Query e repassa o `signal` que o próprio
 * React Query fornece ao `queryFn`.
 */
export function consultarSaldo(
  contaId: string,
  contexto: GovbrContexto,
  options?: RequestOptions,
): Promise<SaldoContaResponse> {
  return razaoClient.consultarSaldo(contaId, contexto, options);
}
