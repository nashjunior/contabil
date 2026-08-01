/**
 * Consulta REAL (ADR-0030 §1): GET /razao/saldo — usada na Tela 1, Saldo por conta PCASP
 * (RAZ-112). Hook fino (ADR-0041): adapta o caso de uso `consultarSaldo` ao React Query e
 * repassa o `signal` que o próprio React Query fornece ao `queryFn`. Só dispara quando há
 * `contaId` selecionado (`enabled`) — não há valor default a consultar.
 */
import { useQuery } from '@tanstack/react-query';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { consultarSaldo } from '../application/consultarSaldo';

export function saldoContaKey(enteId: string, contaId: string) {
  return ['razao-saldo', enteId, contaId] as const;
}

export function useSaldoConta(contaId: string | null) {
  const contexto = useGovbrContexto();

  return useQuery({
    queryKey: saldoContaKey(contexto.enteId, contaId ?? ''),
    queryFn: ({ signal }) => consultarSaldo(contaId as string, contexto, { signal }),
    enabled: contaId !== null,
  });
}
