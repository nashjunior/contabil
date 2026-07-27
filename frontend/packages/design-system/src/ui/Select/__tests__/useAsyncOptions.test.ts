/**
 * Testes do design pattern async isolado (RAZ-122): debounce, cancelamento de request
 * obsoleto (AbortController) e estados loading/vazio/erro — independentes do componente de UI.
 */
import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAsyncOptions } from '../useAsyncOptions';
import type { AsyncOptionsPage } from '../useAsyncOptions';

describe('useAsyncOptions — debounce e cancelamento', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('só chama fetchOptions depois do debounce', () => {
    const fetchOptions = vi.fn().mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useAsyncOptions({ fetchOptions, debounceMs: 300 }));

    act(() => result.current.setQuery('a'));
    expect(fetchOptions).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(299);
    });
    expect(fetchOptions).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(fetchOptions).toHaveBeenCalledTimes(1);
    expect(fetchOptions).toHaveBeenCalledWith(
      expect.objectContaining({ query: 'a', page: 0 }),
    );
  });

  it('reinicia o debounce a cada tecla — só a última query dispara busca', () => {
    const fetchOptions = vi.fn().mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useAsyncOptions({ fetchOptions, debounceMs: 300 }));

    act(() => result.current.setQuery('c'));
    act(() => {
      vi.advanceTimersByTime(200);
    });
    act(() => result.current.setQuery('ca'));
    act(() => {
      vi.advanceTimersByTime(200);
    });
    act(() => result.current.setQuery('cai'));

    expect(fetchOptions).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(fetchOptions).toHaveBeenCalledTimes(1);
    expect(fetchOptions).toHaveBeenCalledWith(expect.objectContaining({ query: 'cai' }));
  });

  it('cancela (AbortController) a busca anterior quando uma nova query chega antes da resposta', () => {
    const signals: AbortSignal[] = [];
    const fetchOptions = vi.fn().mockImplementation(({ signal }: { signal: AbortSignal }) => {
      signals.push(signal);
      return new Promise(() => {});
    });
    const { result } = renderHook(() => useAsyncOptions({ fetchOptions, debounceMs: 300 }));

    act(() => result.current.setQuery('a'));
    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(signals).toHaveLength(1);
    expect(signals[0].aborted).toBe(false);

    act(() => result.current.setQuery('ab'));
    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(signals).toHaveLength(2);
    expect(signals[0].aborted).toBe(true);
    expect(signals[1].aborted).toBe(false);
  });
});

describe('useAsyncOptions — estados loading/vazio/erro', () => {
  it('loading -> success com resultados', async () => {
    let resolveFetch: (page: AsyncOptionsPage) => void = () => {};
    const fetchOptions = vi.fn().mockImplementation(
      () => new Promise<AsyncOptionsPage>((resolve) => { resolveFetch = resolve; }),
    );
    const { result } = renderHook(() => useAsyncOptions({ fetchOptions, debounceMs: 0 }));

    act(() => result.current.setQuery('cai'));
    await waitFor(() => expect(result.current.status).toBe('loading'));

    act(() => resolveFetch({ options: [{ value: '1', label: 'Caixa' }] }));
    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.options).toEqual([{ value: '1', label: 'Caixa' }]);
  });

  it('loading -> empty quando não há resultados', async () => {
    const fetchOptions = vi.fn().mockResolvedValue({ options: [] });
    const { result } = renderHook(() => useAsyncOptions({ fetchOptions, debounceMs: 0 }));

    act(() => result.current.setQuery('zzz'));
    await waitFor(() => expect(result.current.status).toBe('empty'));
  });

  it('loading -> error quando a busca falha', async () => {
    const fetchOptions = vi.fn().mockRejectedValue(new Error('falha de rede'));
    const { result } = renderHook(() => useAsyncOptions({ fetchOptions, debounceMs: 0 }));

    act(() => result.current.setQuery('cai'));
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error?.message).toBe('falha de rede');
  });

  it('respeita minChars — não busca abaixo do mínimo de caracteres', () => {
    const fetchOptions = vi.fn();
    const { result } = renderHook(() => useAsyncOptions({ fetchOptions, debounceMs: 0, minChars: 2 }));

    act(() => result.current.setQuery('a'));
    expect(fetchOptions).not.toHaveBeenCalled();
    expect(result.current.status).toBe('idle');
  });

  it('usa cache — repetir a mesma query não busca de novo', async () => {
    const fetchOptions = vi.fn().mockResolvedValue({ options: [{ value: '1', label: 'Caixa' }] });
    const { result } = renderHook(() => useAsyncOptions({ fetchOptions, debounceMs: 0 }));

    act(() => result.current.setQuery('cai'));
    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(fetchOptions).toHaveBeenCalledTimes(1);

    act(() => result.current.setQuery(''));
    await waitFor(() => expect(fetchOptions).toHaveBeenCalledTimes(2));

    act(() => result.current.setQuery('cai'));
    await waitFor(() => expect(result.current.options).toEqual([{ value: '1', label: 'Caixa' }]));

    expect(fetchOptions).toHaveBeenCalledTimes(2); // repetir 'cai' não refaz o fetch (resposta em cache)
  });
});
