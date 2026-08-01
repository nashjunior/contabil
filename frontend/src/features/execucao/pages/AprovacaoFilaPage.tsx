/**
 * Fila de aprovação (gate 4-eyes) — leitura real e completa (`consultarFilaAprovacao`, mesmo
 * padrão hook→application→client, ADR-0041) desde a RAZ-221/ADR-0052. A decisão do gate
 * (aprovar/devolver, `execucaoClient.aprovarLiquidacao` já existe e está tipado) segue sem
 * interface: é uma ação IRREVERSÍVEL (ADR-0023) que a RAZ-230 optou por não decidir sozinha —
 * ver RAZ-236 (review de UX com a Paula: confirmação, motivo obrigatório na devolução,
 * mapeamento de erro 403/409).
 */
import { useAuth } from '../../../shared/auth/AuthContext';
import { FeatureNav } from '../../../shared/components/FeatureNav';
import { FilaAprovacaoList } from '../components/FilaAprovacaoList';
import { useFilaAprovacao } from '../api/useFilaAprovacao';

export function AprovacaoFilaPage() {
  const { sessao, sair } = useAuth();
  const { data, isLoading } = useFilaAprovacao('pendente');

  return (
    <main style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--spacing-lg)' }}>
        <h1>Fila de aprovação</h1>
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

      <section aria-labelledby="fila-titulo">
        <h2 id="fila-titulo">Liquidações pendentes de decisão</h2>
        {isLoading ? <p role="status">Carregando…</p> : <FilaAprovacaoList itens={data?.itens ?? []} />}
        <p role="note">
          A decisão do gate (aprovar/devolver) ainda não tem interface nesta tela — aguardando
          review de UX (RAZ-236) antes de implementar, já que é uma ação irreversível.
        </p>
      </section>
    </main>
  );
}
