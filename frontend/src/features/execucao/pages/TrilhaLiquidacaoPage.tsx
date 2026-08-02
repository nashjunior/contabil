/** Trilha dedicada de uma liquidação (RAZ-221/ADR-0052) — GET real (ADR-0029 §3), ator mascarado. */
import { Link, useParams } from 'react-router-dom';
import { PageLayout } from '../../../shared/components/PageLayout';
import { TrilhaLiquidacaoList } from '../components/TrilhaLiquidacaoList';
import { useTrilhaLiquidacao } from '../api/useTrilhaLiquidacao';

export function TrilhaLiquidacaoPage() {
  const { id } = useParams<{ id: string }>();
  const { data, isLoading } = useTrilhaLiquidacao(id ?? '');

  return (
    <PageLayout titulo="Trilha da liquidação">
      <p>
        <Link to="/execucao/aprovacoes">← Voltar para a fila de aprovação</Link>
      </p>

      <section aria-labelledby="trilha-titulo">
        <h2 id="trilha-titulo">Eventos</h2>
        {isLoading ? <p role="status">Carregando…</p> : <TrilhaLiquidacaoList eventos={data?.eventos ?? []} />}
      </section>
    </PageLayout>
  );
}
