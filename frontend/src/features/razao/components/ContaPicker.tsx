/**
 * Picker de Conta PCASP (Figma página 13/RAZ-142, node 96:610) — combobox assíncrono com
 * busca (`GET /razao/contas?busca=&cursor=&limit=`), reusando o `Select` compound do
 * design system + `useAsyncOptions` (ADR-0032/RAZ-194: mesmo padrão de combobox
 * single-select do Seletor de Ente). Linha sintética (não-escriturável) vem visível mas
 * não-selecionável (`SelectOption.disabled`); a indentação hierárquica (`depth`) é derivada
 * do nº de segmentos do código (`depthFromCodigo`) — a API devolve lista plana (ADR-0030 §6).
 *
 * Paginação por cursor opaco, não numerada: `useAsyncOptions` pagina por índice de página
 * (`page: number`), então o cursor de cada página é guardado aqui (`cursorsRef`), chaveado
 * por `query::page` — mesma chave de cache que `useAsyncOptions` usa internamente — para não
 * misturar o cursor de uma busca com o de outra ao alternar entre buscas já visitadas (cache
 * quente de `useAsyncOptions` pula `fetchOptions` só quando o par já foi buscado; indexar por
 * `page` sozinho reusaria o cursor da ÚLTIMA busca, não da busca da chamada atual). A página 0
 * nunca precisa de cursor, então nunca depende deste mapa.
 */
import { useCallback, useMemo, useRef } from 'react';
import { Select, useAsyncOptions, type FetchOptions, type SelectOption } from '@siafic/design-system';
import { useGovbrContexto } from '../../../shared/auth/AuthContext';
import { consultarContas } from '../application/consultarContas';
import { depthFromCodigo } from '../lib/depthFromCodigo';
import type { ContaResumo } from '../../../shared/api/client';

function contaParaOption(conta: ContaResumo): SelectOption {
  return {
    value: conta.id,
    label: `${conta.codigo} — ${conta.descricao}`,
    searchValue: `${conta.codigo} ${conta.descricao}`,
    disabled: !conta.escrituravel,
    depth: depthFromCodigo(conta.codigo),
  };
}

export function ContaPicker({
  value,
  onChange,
  id,
  ariaLabel = 'Conta contábil (PCASP)',
}: {
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
      const resposta = await consultarContas({ busca: query || undefined, cursor, limit: 20 }, contexto, { signal });
      cursorsRef.current[`${query}::${page + 1}`] = resposta.proximoCursor ?? undefined;
      return {
        options: resposta.itens.map(contaParaOption),
        hasMore: resposta.proximoCursor != null,
      };
    },
    [contexto],
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
      placeholder="Buscar por código ou descrição…"
    >
      <Select.Trigger aria-label={ariaLabel} />
      <Select.Options />
    </Select>
  );
}
