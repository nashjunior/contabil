import {
  transparenciaClient,
  type BulkManifestResponse,
} from '../../../shared/api/transparenciaClient';
import type { RequestOptions } from '../../../shared/api/client';

/** Caso de uso puro (ADR-0041): espelha `TransparenciaPublicaController.bulk`. Não recebe
 * filtro — o manifesto (Tela 4) cobre a base completa, sem parâmetros de consulta (ADR-0060
 * D4: "a base completa NÃO aplica os filtros da Tela 1"). */
export function consultarManifestoBulk(enteId: string, options?: RequestOptions): Promise<BulkManifestResponse> {
  return transparenciaClient.bulk(enteId, {}, options);
}
