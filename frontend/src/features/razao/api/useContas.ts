/**
 * Consulta REAL (ADR-0030 §6): GET /razao/contas — catálogo/busca de conta PCASP, usada na
 * Tela 4, Catálogo de Contas PCASP (RAZ-112/RAZ-142). Hook fino (ADR-0041): adapta o caso de
 * uso `consultarContas` ao React Query e repassa o `signal` que o próprio React Query
 * fornece ao `queryFn`. Paginação por cursor opaco (não numerada, ADR-0030 §6) — a página
 * chamadora decide se acumula `itens` entre páginas ("Carregar mais") ou troca a busca.
 */
import { useQuery } from '@tanstack/react-query';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { consultarContas } from '../application/consultarContas';

export function contasKey(enteId: string, busca: string, cursor?: string) {
  return ['razao-contas', enteId, busca, cursor ?? null] as const;
}

export function useContas(busca: string, cursor?: string) {
  const contexto = useGovbrContexto();

  return useQuery({
    queryKey: contasKey(contexto.enteId, busca, cursor),
    queryFn: ({ signal }) => consultarContas({ busca: busca || undefined, cursor }, contexto, { signal }),
  });
}
