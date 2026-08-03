import {
  transparenciaClient,
  type FiltroDespesasPublicas,
  type TotalizacaoTransparenciaResponse,
} from '../../../shared/api/transparenciaClient';
import type { RequestOptions } from '../../../shared/api/client';

/** Caso de uso puro (ADR-0041): espelha `TransparenciaPublicaController.totalizacoes`. */
export function totalizarDespesasPublicas(
  enteId: string,
  filtro: FiltroDespesasPublicas,
  options?: RequestOptions,
): Promise<TotalizacaoTransparenciaResponse> {
  return transparenciaClient.totalizacoes(enteId, filtro, options);
}
