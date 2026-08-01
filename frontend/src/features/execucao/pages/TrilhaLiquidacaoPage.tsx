/** Trilha dedicada de uma liquidação (RAZ-221/ADR-0052) — GET real (ADR-0029 §3), ator mascarado. */
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../../../shared/auth/AuthContext';
import { FeatureNav } from '../../../shared/components/FeatureNav';
import { TrilhaLiquidacaoList } from '../components/TrilhaLiquidacaoList';
import { useTrilhaLiquidacao } from '../api/useTrilhaLiquidacao';

export function TrilhaLiquidacaoPage() {
  const { sessao, sair } = useAuth();
  const { id } = useParams<{ id: string }>();
  const { data, isLoading } = useTrilhaLiquidacao(id ?? '');

  return (
    <main style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--spacing-lg)' }}>
        <h1>Trilha da liquidação</h1>
        {sessao && (
          <p>
            {sessao.enteNome ?? sessao.enteId} · CPF {sessao.cpfMascarado}{' '}
            <button type="button" onClick={sair}>
              Sair
            </button>
          </p>
        )}
      </header>

      <FeatureNav />

      <p>
        <Link to="/execucao/aprovacoes">← Voltar para a fila de aprovação</Link>
      </p>

      <section aria-labelledby="trilha-titulo">
        <h2 id="trilha-titulo">Eventos</h2>
        {isLoading ? <p role="status">Carregando…</p> : <TrilhaLiquidacaoList eventos={data?.eventos ?? []} />}
      </section>
    </main>
  );
}
