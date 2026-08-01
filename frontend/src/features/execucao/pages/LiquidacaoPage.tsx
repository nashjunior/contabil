/**
 * Registrar liquidação — RAZ-221/ADR-0052: esqueleto de rota/página, não a tela final.
 * `execucaoClient.registrarLiquidacao` já existe e está tipado contra o contrato real
 * (`POST /execucao/liquidacoes`); o formulário (RHF+zod, padrão ADR-0043 do `EmpenhoForm`)
 * fica para a issue-filha de implementação de tela (dono: Bruno).
 */
import { useAuth } from '../../../shared/auth/AuthContext';
import { FeatureNav } from '../../../shared/components/FeatureNav';

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

      <p role="note">
        Formulário ainda não implementado nesta tela — ver follow-up de implementação
        (RAZ-221). O client de API (<code>execucaoClient.registrarLiquidacao</code>) já está
        pronto e tipado contra o contrato real.
      </p>
    </main>
  );
}
