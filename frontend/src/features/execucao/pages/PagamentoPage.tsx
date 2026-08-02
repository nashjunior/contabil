/**
 * Registrar pagamento (RAZ-230) — formulário RHF+zod (ADR-0043), fecha o gap deixado pela
 * RAZ-221/ADR-0052 (esqueleto de rota/página, decisão 3: formulário ficava para issue-filha).
 * `PagamentoForm` fala só com `useRegistrarPagamento` (nunca `execucaoClient` direto).
 */
import { PageLayout } from '../../../shared/components/PageLayout';
import { PagamentoForm } from '../components/PagamentoForm';

export function PagamentoPage() {
  return (
    <PageLayout titulo="Registrar pagamento">
      <PagamentoForm />
    </PageLayout>
  );
}
