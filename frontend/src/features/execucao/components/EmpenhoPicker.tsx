/**
 * Combo de empenho do `LiquidacaoForm` (RAZ-230) — modo SÍNCRONO do `Select` (README "Uso
 * síncrono"), diferente do `DotacaoPicker` (assíncrono): `GET /execucao/empenhos`
 * (RAZ-121/RAZ-199) não tem parâmetro `busca` server-side (só `exercicio`/intervalo de datas,
 * ao contrário de `GET /execucao/dotacoes`) — gap documentado, mesma convenção de "1 tela
 * mínima" já usada para credorId/unidadeGestoraId/contratoId no `EmpenhoForm` (ver README
 * "Gaps sinalizados"). Reusa `useEmpenhosRegistrados` (já usado por `ExecucaoList`), escopado
 * ao exercício corrente, e filtra localmente pelo texto digitado (via `searchValue`) — cobre o
 * caso comum (liquidar um empenho do ano corrente); liquidar um empenho de exercício anterior
 * fica fora desta entrega, mesmo gap de busca server-side.
 */
import { useMemo } from 'react';
import { Select, type SelectOption } from '@siafic/design-system';
import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import { useEmpenhosRegistrados } from '../api/useEmpenhosRegistrados';
import type { EmpenhoRegistradoResponse } from '../../../shared/api/client';

function empenhoParaOption(empenho: EmpenhoRegistradoResponse): SelectOption {
  return {
    value: empenho.id,
    label: `${empenho.exercicio}/${empenho.numeroSequencial} — ${empenho.historico} (${formatMoneyBRL(empenho.valor)})`,
    searchValue: `${empenho.numeroSequencial} ${empenho.historico}`,
  };
}

export function EmpenhoPicker({
  value,
  onChange,
  id,
  ariaLabel = 'Empenho',
}: {
  value: string | null;
  onChange: (value: string | null) => void;
  id?: string;
  ariaLabel?: string;
}) {
  const exercicio = new Date().getFullYear();
  const { data, isLoading } = useEmpenhosRegistrados(exercicio);

  const options = useMemo(() => (data?.itens ?? []).map(empenhoParaOption), [data]);

  return (
    <Select
      id={id}
      value={value}
      onChange={onChange}
      options={options}
      status={isLoading ? 'loading' : undefined}
      placeholder="Buscar por número ou histórico…"
    >
      <Select.Trigger aria-label={ariaLabel} />
      <Select.Options />
    </Select>
  );
}
