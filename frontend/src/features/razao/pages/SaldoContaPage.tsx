/**
 * Tela 1 — Saldo por conta PCASP (Figma página 12/RAZ-112): busca a conta via
 * `ContaPicker` (Picker de Conta PCASP, RAZ-142) e consulta `GET /razao/saldo` real.
 */
import { useState } from 'react';
import { useAuth } from '../../../shared/auth/AuthContext';
import { FeatureNav } from '../../../shared/components/FeatureNav';
import { ContaPicker } from '../components/ContaPicker';
import { SaldoContaCard } from '../components/SaldoContaCard';

export function SaldoContaPage() {
  const { sessao, sair } = useAuth();
  const [contaId, setContaId] = useState<string | null>(null);

  return (
    <main style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--spacing-lg)' }}>
        <h1>Saldo por conta</h1>
        {sessao && (
          <p>
            {sessao.enteNome} · CPF {sessao.cpfMascarado}{' '}
            <button type="button" onClick={sair}>
              Sair
            </button>
          </p>
        )}
      </header>

      <FeatureNav />

      <section aria-labelledby="buscar-conta-titulo">
        <h2 id="buscar-conta-titulo">Conta contábil</h2>
        <ContaPicker value={contaId} onChange={setContaId} id="conta-picker-saldo" />
      </section>

      <section aria-labelledby="saldo-titulo" style={{ marginTop: 'var(--spacing-lg)' }}>
        <h2 id="saldo-titulo">Saldo consultado</h2>
        <SaldoContaCard contaId={contaId} />
      </section>
    </main>
  );
}
