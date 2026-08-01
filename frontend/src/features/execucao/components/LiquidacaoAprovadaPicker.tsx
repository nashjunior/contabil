/**
 * Combo de liquidação aprovada do `PagamentoForm` (RAZ-230) — modo SÍNCRONO do `Select`, mesmo
 * padrão do `EmpenhoPicker`: reusa `useFilaAprovacao('aprovada')` (já usado por
 * `AprovacaoFilaPage`) em vez de introduzir um novo endpoint — só liquidação aprovada pode
 * receber pagamento (`RegistrarPagamento.executar`, `PagamentoNaoAprovadoException`).
 */
import { useMemo } from 'react';
import { Select, type SelectOption } from '@siafic/design-system';
import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import { useFilaAprovacao } from '../api/useFilaAprovacao';
import type { ItemFilaAprovacao } from '../../../shared/api/client';

function itemParaOption(item: ItemFilaAprovacao): SelectOption {
  return {
    value: item.id,
    label: `Empenho ${item.exercicioEmpenho}/${item.numeroEmpenho} — ${formatMoneyBRL(item.valor)} (competência ${item.dataCompetencia})`,
    searchValue: `${item.numeroEmpenho}`,
  };
}

export function LiquidacaoAprovadaPicker({
  value,
  onChange,
  id,
  ariaLabel = 'Liquidação aprovada',
}: {
  value: string | null;
  onChange: (value: string | null) => void;
  id?: string;
  ariaLabel?: string;
}) {
  const { data, isLoading } = useFilaAprovacao('aprovada');

  const options = useMemo(() => (data?.itens ?? []).map(itemParaOption), [data]);

  return (
    <Select
      id={id}
      value={value}
      onChange={onChange}
      options={options}
      status={isLoading ? 'loading' : undefined}
      placeholder="Buscar por número do empenho…"
    >
      <Select.Trigger aria-label={ariaLabel} />
      <Select.Options />
    </Select>
  );
}
