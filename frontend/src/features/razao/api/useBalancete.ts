/**
 * Consulta REAL (ADR-0030 §1): GET /razao/balancete — usada na Tela 2, Balancete (RAZ-112).
 * Hook fino (ADR-0041): adapta o caso de uso `consultarBalancete` ao React Query e repassa
 * o `signal` que o próprio React Query fornece ao `queryFn`.
 */
import { useQuery } from '@tanstack/react-query';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { consultarBalancete } from '../application/consultarBalancete';

export function balanceteKey(enteId: string, exercicio: number, mes: number) {
  return ['razao-balancete', enteId, exercicio, mes] as const;
}

export function useBalancete(exercicio: number, mes: number) {
  const contexto = useGovbrContexto();

  return useQuery({
    queryKey: balanceteKey(contexto.enteId, exercicio, mes),
    queryFn: ({ signal }) => consultarBalancete(exercicio, mes, contexto, { signal }),
  });
}
