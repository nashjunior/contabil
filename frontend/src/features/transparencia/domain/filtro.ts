/**
 * Estado da `Barra de Filtros — Transparência (compartilhada Lista/Totais)` (design-system-
 * tokens-componentes.md §6.1) — os mesmos 9 campos que a tela do Figma expõe, nem um a mais
 * (a API não tem outros filtros). Vive na URL (`useSearchParams`, ver `ListaTotaisPage`), não
 * em estado local isolado: implementa UX-2 de verdade (a mesma instância de filtro serve Lista
 * e Totais porque as duas leem a mesma URL) e torna a consulta compartilhável/voltável pelo
 * navegador — sem isso, "Aplicar filtros" perderia o estado a cada re-render de aba.
 */
import type { FiltroDespesasPublicas } from '../../../shared/api/transparenciaClient';

export type FiltroFormState = {
  estagio: string;
  dataInicio: string;
  dataFim: string;
  numeroEmpenho: string;
  funcao: string;
  ordenarPor: string;
  credorId: string;
  orgaoId: string;
  contratoId: string;
};

export const FILTRO_VAZIO: FiltroFormState = {
  estagio: '',
  dataInicio: '',
  dataFim: '',
  numeroEmpenho: '',
  funcao: '',
  ordenarPor: 'publicadoEm',
  credorId: '',
  orgaoId: '',
  contratoId: '',
};

const CAMPOS_FILTRO = Object.keys(FILTRO_VAZIO) as Array<keyof FiltroFormState>;

export function filtroDeSearchParams(params: URLSearchParams): FiltroFormState {
  const form = { ...FILTRO_VAZIO };
  for (const campo of CAMPOS_FILTRO) {
    const valor = params.get(campo);
    if (valor !== null) form[campo] = valor;
  }
  return form;
}

/** Escreve os campos do filtro numa cópia de `params`, preservando outras chaves (ex.: `aba`)
 * já presentes — quem chama decide o que mais entra na URL. */
export function aplicarFiltroEmSearchParams(params: URLSearchParams, form: FiltroFormState): URLSearchParams {
  const novo = new URLSearchParams(params);
  for (const campo of CAMPOS_FILTRO) {
    const valor = form[campo];
    if (valor) novo.set(campo, valor);
    else novo.delete(campo);
  }
  return novo;
}

export function limparFiltroEmSearchParams(params: URLSearchParams): URLSearchParams {
  const novo = new URLSearchParams(params);
  for (const campo of CAMPOS_FILTRO) novo.delete(campo);
  return novo;
}

/** Para a chamada à API — `despesas`/`totalizacoes`/`bulk` aceitam o mesmo shape
 * (`FiltroDespesasPublicas`); `cursor`/`limit` ficam de fora (não são campos de formulário,
 * quem pagina cuida deles à parte via `useInfiniteQuery`). */
export function filtroParaApi(form: FiltroFormState): FiltroDespesasPublicas {
  return {
    estagio: form.estagio || undefined,
    dataInicio: form.dataInicio || undefined,
    dataFim: form.dataFim || undefined,
    numeroEmpenho: form.numeroEmpenho || undefined,
    funcao: form.funcao || undefined,
    ordenarPor: form.ordenarPor || undefined,
    credorId: form.credorId || undefined,
    orgaoId: form.orgaoId || undefined,
    contratoId: form.contratoId || undefined,
  };
}
