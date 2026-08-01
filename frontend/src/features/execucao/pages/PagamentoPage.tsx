/**
 * Registrar pagamento (RAZ-230) — formulário RHF+zod (ADR-0043), fecha o gap deixado pela
 * RAZ-221/ADR-0052 (esqueleto de rota/página, decisão 3: formulário ficava para issue-filha).
 * `PagamentoForm` fala só com `useRegistrarPagamento` (nunca `execucaoClient` direto).
 */
import { useAuth } from '../../../shared/auth/AuthContext';
import { FeatureNav } from '../../../shared/components/FeatureNav';
import { PagamentoForm } from '../components/PagamentoForm';

export function PagamentoPage() {
  const { sessao, sair } = useAuth();

  return (
    <main style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--spacing-lg)' }}>
        <h1>Registrar pagamento</h1>
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

      <PagamentoForm />
    </main>
  );
}
