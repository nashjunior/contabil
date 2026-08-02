/**
 * Tela 1 — Saldo por conta PCASP (Figma página 12/RAZ-112): busca a conta via
 * `ContaPicker` (Picker de Conta PCASP, RAZ-142) e consulta `GET /razao/saldo` real.
 */
import { useState } from 'react';
import { PageLayout } from '../../../shared/components/PageLayout';
import { ContaPicker } from '../components/ContaPicker';
import { SaldoContaCard } from '../components/SaldoContaCard';

export function SaldoContaPage() {
  const [contaId, setContaId] = useState<string | null>(null);

  return (
    <PageLayout titulo="Saldo por conta">
      <section aria-labelledby="buscar-conta-titulo">
        <h2 id="buscar-conta-titulo">Conta contábil</h2>
        <ContaPicker value={contaId} onChange={setContaId} id="conta-picker-saldo" />
      </section>

      <section aria-labelledby="saldo-titulo" style={{ marginTop: 'var(--spacing-lg)' }}>
        <h2 id="saldo-titulo">Saldo consultado</h2>
        <SaldoContaCard contaId={contaId} />
      </section>
    </PageLayout>
  );
}
