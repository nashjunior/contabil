import {
  transparenciaClient,
  type DespesasPublicasResponse,
  type FiltroDespesasPublicas,
} from '../../../shared/api/transparenciaClient';
import type { RequestOptions } from '../../../shared/api/client';

/** Caso de uso puro (ADR-0041): espelha `TransparenciaPublicaController.despesas`. Sem
 * `GovbrContexto` — a superfície é pública, `enteId` é o único escopo. */
export function consultarDespesasPublicas(
  enteId: string,
  filtro: FiltroDespesasPublicas,
  options?: RequestOptions,
): Promise<DespesasPublicasResponse> {
  return transparenciaClient.despesas(enteId, filtro, options);
}
