/**
 * Design pattern dedicado (RAZ-122): toda a lógica assíncrona de opções do Select — debounce,
 * cancelamento de request obsoleto (AbortController), estados loading/vazio/erro e
 * cache/paginação — fica isolada aqui, fora do componente de UI. O `Select` não sabe nada de
 * fetch; só recebe `options`/`query`/`onQueryChange`/`status`/`hasMore`/`onLoadMore` (ver
 * README.md desta pasta para o exemplo completo).
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import type { SelectOption, SelectStatus } from './Select';

export type AsyncOptionsPage = {
  options: SelectOption[];
  /** Há mais páginas a carregar (paginação opcional via `loadMore`). */
  hasMore?: boolean;
};

export type FetchOptions = (params: { query: string; page: number; signal: AbortSignal }) => Promise<AsyncOptionsPage>;

type UseAsyncOptionsParams = {
  fetchOptions: FetchOptions;
  /** Atraso do debounce em ms antes de disparar a busca. Default: 300. */
  debounceMs?: number;
  /** Não busca enquanto `query.length` for menor que este valor. Default: 0 (busca já no mount). */
  minChars?: number;
};

type UseAsyncOptionsResult = {
  query: string;
  setQuery: (query: string) => void;
  options: SelectOption[];
  status: SelectStatus;
  error: Error | null;
  hasMore: boolean;
  loadMore: () => void;
};

const DEFAULT_DEBOUNCE_MS = 300;

export function useAsyncOptions({
  fetchOptions,
  debounceMs = DEFAULT_DEBOUNCE_MS,
  minChars = 0,
}: UseAsyncOptionsParams): UseAsyncOptionsResult {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [options, setOptions] = useState<SelectOption[]>([]);
  const [status, setStatus] = useState<SelectStatus>('idle');
  const [error, setError] = useState<Error | null>(null);
  const [hasMore, setHasMore] = useState(false);

  const abortRef = useRef<AbortController | null>(null);
  const cacheRef = useRef(new Map<string, AsyncOptionsPage>());
  const fetchOptionsRef = useRef(fetchOptions);
  fetchOptionsRef.current = fetchOptions;

  const runFetch = useCallback(
    (nextQuery: string, nextPage: number, append: boolean) => {
      abortRef.current?.abort();

      if (nextQuery.length < minChars) {
        abortRef.current = null;
        setOptions([]);
        setStatus('idle');
        setHasMore(false);
        return;
      }

      const cacheKey = `${nextQuery}::${nextPage}`;
      const cached = cacheRef.current.get(cacheKey);
      if (cached) {
        abortRef.current = null;
        setOptions((current) => (append ? [...current, ...cached.options] : cached.options));
        setStatus(!append && cached.options.length === 0 ? 'empty' : 'success');
        setHasMore(Boolean(cached.hasMore));
        return;
      }

      const controller = new AbortController();
      abortRef.current = controller;
      setStatus('loading');
      setError(null);

      fetchOptionsRef
        .current({ query: nextQuery, page: nextPage, signal: controller.signal })
        .then((result) => {
          if (controller.signal.aborted) return;
          cacheRef.current.set(cacheKey, result);
          setOptions((current) => (append ? [...current, ...result.options] : result.options));
          setStatus(!append && result.options.length === 0 ? 'empty' : 'success');
          setHasMore(Boolean(result.hasMore));
        })
        .catch((err: unknown) => {
          if (controller.signal.aborted) return;
          setError(err instanceof Error ? err : new Error('Falha ao carregar opções.'));
          setStatus('error');
        });
    },
    [minChars],
  );

  useEffect(() => {
    setPage(0);
    const timer = setTimeout(() => runFetch(query, 0, false), debounceMs);
    return () => clearTimeout(timer);
  }, [query, debounceMs, runFetch]);

  useEffect(() => () => abortRef.current?.abort(), []);

  const loadMore = useCallback(() => {
    if (status === 'loading' || !hasMore) return;
    const nextPage = page + 1;
    setPage(nextPage);
    runFetch(query, nextPage, true);
  }, [status, hasMore, page, query, runFetch]);

  return { query, setQuery, options, status, error, hasMore, loadMore };
}
