/**
 * Registrar liquidação (RAZ-230) — formulário RHF+zod (ADR-0043), fecha o gap deixado pela
 * RAZ-221/ADR-0052 (esqueleto de rota/página, decisão 3: formulário ficava para issue-filha).
 * `LiquidacaoForm` fala só com `useRegistrarLiquidacao` (nunca `execucaoClient` direto).
 */
import { useAuth } from '../../../shared/auth/AuthContext';
import { FeatureNav } from '../../../shared/components/FeatureNav';
import { LiquidacaoForm } from '../components/LiquidacaoForm';

export function LiquidacaoPage() {
  const { sessao, sair } = useAuth();

  return (
    <main style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--spacing-lg)' }}>
        <h1>Registrar liquidação</h1>
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

      <LiquidacaoForm />
    </main>
  );
}
