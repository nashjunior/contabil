/**
 * Fila de aprovação (gate 4-eyes) — RAZ-221/ADR-0052: esqueleto de arquitetura, não a
 * tela final. Lado de leitura (`GET /execucao/liquidacoes`, ADR-0029 §1) real e completo,
 * mesmo padrão hook→application→client das demais páginas (ADR-0041). A decisão do gate
 * (aprovar/devolver, `execucaoClient.aprovarLiquidacao`, já existe no client) ainda não
 * tem interface — fica para a issue-filha de implementação de tela (dono: Bruno), porque
 * é uma ação irreversível que merece revisão de UX própria antes de virar um botão.
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
          A decisão do gate (aprovar/devolver) ainda não tem interface nesta tela — ver
          follow-up de implementação (RAZ-221).
        </p>
      </section>
    </main>
  );
}
