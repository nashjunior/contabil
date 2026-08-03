import { transparenciaClient } from '../../../shared/api/transparenciaClient';
import type { RequestOptions } from '../../../shared/api/client';

/** Caso de uso puro (ADR-0041): espelha `TransparenciaPublicaController.dicionarioDados`.
 * Devolve o markdown cru — UX-5 exige renderizar exatamente a fonte da API, não um texto
 * reescrito à parte. */
export function consultarDicionarioDados(enteId: string, options?: RequestOptions): Promise<string> {
  return transparenciaClient.dicionarioDados(enteId, options);
}
