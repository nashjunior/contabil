/**
 * Tela 4 — Catálogo de Contas PCASP (Figma página 12/RAZ-142): navegação/consulta do plano
 * de contas via `GET /razao/contas` real. Paginação por cursor opaco (ADR-0030 §6) — sem
 * paginação numerada, só "Carregar mais" acumulando itens da busca corrente.
 */
import { useEffect, useState } from 'react';
import { PageLayout } from '../../../shared/components/PageLayout';
import { useContas } from '../api/useContas';
import { ContasCatalogoTable } from '../components/ContasCatalogoTable';
import type { ContaResumo } from '../../../shared/api/client';

export function CatalogoContasPage() {
  const [busca, setBusca] = useState('');
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [itens, setItens] = useState<ContaResumo[]>([]);
  const { data, isLoading, isError, error } = useContas(busca, cursor);

  useEffect(() => {
    if (!data) return;
    setItens((atual) => (cursor ? [...atual, ...data.itens] : data.itens));
  }, [data, cursor]);

  function handleBuscaChange(next: string) {
    setBusca(next);
    setCursor(undefined);
  }

  return (
    <PageLayout titulo="Catálogo de contas PCASP" largura="ampla">
      <section aria-labelledby="busca-contas-titulo">
        <h2 id="busca-contas-titulo">Buscar conta</h2>
        <label>
          Código ou descrição
          <br />
          <input
            type="search"
            value={busca}
            onChange={(event) => handleBuscaChange(event.target.value)}
            placeholder="Ex.: 1.1 ou Caixa"
          />
        </label>
      </section>

      <section aria-labelledby="lista-contas-titulo" style={{ marginTop: 'var(--spacing-lg)' }}>
        <h2 id="lista-contas-titulo">Contas</h2>
        {isError && (
          <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
            Não foi possível consultar o catálogo de contas: {error.message}
          </p>
        )}
        <ContasCatalogoTable itens={itens} />
        {isLoading && <p role="status">Consultando contas…</p>}
        {data?.proximoCursor && (
          <button type="button" onClick={() => setCursor(data.proximoCursor ?? undefined)} disabled={isLoading}>
            Carregar mais
          </button>
        )}
      </section>
    </PageLayout>
  );
}
