/** GET /transparencia/{enteId}/dicionario-dados — Tela 3. */
import { useQuery } from '@tanstack/react-query';
import { consultarDicionarioDados } from '../application/consultarDicionarioDados';

export function dicionarioDadosKey(enteId: string) {
  return ['transparencia-dicionario-dados', enteId] as const;
}

export function useDicionarioDados(enteId: string) {
  return useQuery({
    queryKey: dicionarioDadosKey(enteId),
    queryFn: ({ signal }) => consultarDicionarioDados(enteId, { signal }),
  });
}
