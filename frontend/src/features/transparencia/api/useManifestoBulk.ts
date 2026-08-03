/** GET /transparencia/{enteId}/despesas/bulk — Tela 4. */
import { useQuery } from '@tanstack/react-query';
import { consultarManifestoBulk } from '../application/consultarManifestoBulk';

export function manifestoBulkKey(enteId: string) {
  return ['transparencia-bulk', enteId] as const;
}

export function useManifestoBulk(enteId: string) {
  return useQuery({
    queryKey: manifestoBulkKey(enteId),
    queryFn: ({ signal }) => consultarManifestoBulk(enteId, { signal }),
  });
}
