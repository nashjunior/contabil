/**
 * Fila de aprovação (gate 4-eyes) — leitura real e completa (`consultarFilaAprovacao`, mesmo
 * padrão hook→application→client, ADR-0041) desde a RAZ-221/ADR-0052. A decisão do gate
 * (aprovar/devolver) é o botão "Decidir" por linha pendente em `FilaAprovacaoList`, que abre o
 * `GateAprovacaoModal` (RAZ-229/ADR-0055 — UX revisada com a Paula: confirmação por contexto sem
 * segundo modal, motivo obrigatório na devolução, mapeamento de erro 403/409/428).
 */
import { PageLayout } from '../../../shared/components/PageLayout';
import { FilaAprovacaoList } from '../components/FilaAprovacaoList';
import { useFilaAprovacao } from '../api/useFilaAprovacao';

export function AprovacaoFilaPage() {
  const { data, isLoading } = useFilaAprovacao('pendente');

  return (
    <PageLayout titulo="Fila de aprovação">
      <section aria-labelledby="fila-titulo">
        <h2 id="fila-titulo">Liquidações pendentes de decisão</h2>
        {isLoading ? <p role="status">Carregando…</p> : <FilaAprovacaoList itens={data?.itens ?? []} />}
      </section>
    </PageLayout>
  );
}
