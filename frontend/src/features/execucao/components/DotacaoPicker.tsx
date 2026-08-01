/**
 * Combo de dotação do EmpenhoForm (RAZ-199) — combobox assíncrono com busca
 * (`GET /execucao/dotacoes?exercicio=&busca=&cursor=&limit=`, RAZ-148/ADR-0038), reusando
 * o `Select` compound do design system + `useAsyncOptions`, mesmo padrão do `ContaPicker`
 * (`features/razao/components/ContaPicker.tsx`). Rótulo mostra classificação orçamentária +
 * fonte de recurso + saldo disponível (nunca o UUID cru) para o usuário escolher por
 * significado, não por id.
 *
 * `exercicio` é remontado pelo pai via `key` (`EmpenhoForm`): trocar o exercício invalida
 * a busca/cache assíncronos deste picker por inteiro — dotação é sempre escopada a um
 * exercício (não faz sentido reaproveitar cache entre exercícios diferentes).
 */
import { useCallback, useMemo, useRef } from 'react';
import { Select, useAsyncOptions, type FetchOptions, type SelectOption } from '@siafic/design-system';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import { consultarDotacoes } from '../application/consultarDotacoes';
import type { DotacaoResponse } from '../../../shared/api/client';

function dotacaoParaOption(dotacao: DotacaoResponse): SelectOption {
  return {
    value: dotacao.id,
    label: `${dotacao.classificacaoOrcamentaria} — ${dotacao.fonteRecurso} (saldo ${formatMoneyBRL(dotacao.saldoDisponivel)})`,
    searchValue: `${dotacao.classificacaoOrcamentaria} ${dotacao.fonteRecurso}`,
  };
}

export function DotacaoPicker({
  exercicio,
  value,
  onChange,
  id,
  ariaLabel = 'Dotação',
}: {
  exercicio: number;
  value: string | null;
  onChange: (value: string | null) => void;
  id?: string;
  ariaLabel?: string;
}) {
  const contexto = useGovbrContexto();
  const cursorsRef = useRef<Record<string, string | undefined>>({});

  const fetchOptions: FetchOptions = useCallback(
    async ({ query, page, signal }) => {
      const cursor = page === 0 ? undefined : cursorsRef.current[`${query}::${page}`];
      const resposta = await consultarDotacoes(
        { exercicio, busca: query || undefined, cursor, limit: 20 },
        contexto,
        { signal },
      );
      cursorsRef.current[`${query}::${page + 1}`] = resposta.proximoCursor ?? undefined;
      return {
        options: resposta.itens.map(dotacaoParaOption),
        hasMore: resposta.proximoCursor != null,
      };
    },
    [contexto, exercicio],
  );

  const async_ = useAsyncOptions({ fetchOptions });
  const options = useMemo(() => async_.options, [async_.options]);

  return (
    <Select
      id={id}
      value={value}
      onChange={onChange}
      options={options}
      query={async_.query}
      onQueryChange={async_.setQuery}
      status={async_.status}
      errorMessage={async_.error?.message}
      hasMore={async_.hasMore}
      onLoadMore={async_.loadMore}
      placeholder="Buscar por classificação ou fonte de recurso…"
    >
      <Select.Trigger aria-label={ariaLabel} />
      <Select.Options />
    </Select>
  );
}
