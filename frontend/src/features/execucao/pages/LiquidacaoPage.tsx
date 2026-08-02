/**
 * Registrar liquidação (RAZ-230) — formulário RHF+zod (ADR-0043), fecha o gap deixado pela
 * RAZ-221/ADR-0052 (esqueleto de rota/página, decisão 3: formulário ficava para issue-filha).
 * `LiquidacaoForm` fala só com `useRegistrarLiquidacao` (nunca `execucaoClient` direto).
 */
import { PageLayout } from '../../../shared/components/PageLayout';
import { LiquidacaoForm } from '../components/LiquidacaoForm';

export function LiquidacaoPage() {
  return (
    <PageLayout titulo="Registrar liquidação">
      <LiquidacaoForm />
    </PageLayout>
  );
}
