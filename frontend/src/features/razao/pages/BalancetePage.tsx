/**
 * Tela 2 — Balancete (Figma página 12/RAZ-112): consulta `GET /razao/balancete` real para
 * o período selecionado e renderiza a Tabela — Balancete (componente RAZ-112).
 */
import { useState } from 'react';
import { useAuth } from '../../../shared/auth/AuthContext';
import { FeatureNav } from '../../../shared/components/FeatureNav';
import { useBalancete } from '../api/useBalancete';
import { BalanceteTable } from '../components/BalanceteTable';

const AGORA = new Date();

export function BalancetePage() {
  const { sessao, sair } = useAuth();
  const [exercicio, setExercicio] = useState(AGORA.getFullYear());
  const [mes, setMes] = useState(AGORA.getMonth() + 1);
  const { data, isLoading, isError, error } = useBalancete(exercicio, mes);

  return (
    <main style={{ maxWidth: 960, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--spacing-lg)' }}>
        <h1>Balancete</h1>
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

      <section aria-labelledby="periodo-titulo">
        <h2 id="periodo-titulo">Período</h2>
        <div style={{ display: 'flex', gap: 'var(--spacing-md)', alignItems: 'end' }}>
          <label>
            Exercício
            <br />
            <input
              type="number"
              inputMode="numeric"
              value={exercicio}
              onChange={(event) => setExercicio(Number(event.target.value))}
            />
          </label>
          <label>
            Mês
            <br />
            <input
              type="number"
              inputMode="numeric"
              min={1}
              max={13}
              value={mes}
              onChange={(event) => setMes(Number(event.target.value))}
            />
          </label>
        </div>
      </section>

      <section aria-labelledby="balancete-titulo" style={{ marginTop: 'var(--spacing-lg)' }}>
        <h2 id="balancete-titulo">
          Balancete de {String(mes).padStart(2, '0')}/{exercicio}
        </h2>
        {isLoading && <p role="status">Consultando balancete…</p>}
        {isError && (
          <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
            Não foi possível consultar o balancete: {error.message}
          </p>
        )}
        {data && <BalanceteTable balancete={data} />}
      </section>
    </main>
  );
}
