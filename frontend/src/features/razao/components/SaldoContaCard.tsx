import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import { useSaldoConta } from '../api/useSaldoConta';

/** Consulta REAL (GET /razao/saldo) — Tela 1, Saldo por conta PCASP (RAZ-112). */
export function SaldoContaCard({ contaId }: { contaId: string | null }) {
  const { data, isLoading, isError, error } = useSaldoConta(contaId);

  if (!contaId) {
    return <p style={{ color: 'var(--color-text-secondary)' }}>Selecione uma conta para consultar o saldo.</p>;
  }

  if (isLoading) return <p role="status">Consultando saldo…</p>;

  if (isError) {
    return (
      <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
        Não foi possível consultar o saldo: {error.message}
      </p>
    );
  }

  if (!data) return null;

  return (
    <dl
      style={{
        display: 'inline-flex',
        flexDirection: 'column',
        gap: 'var(--spacing-2xs)',
        padding: 'var(--spacing-lg)',
        border: '1px solid var(--color-border-default)',
        borderRadius: 'var(--radius-md)',
        background: 'var(--color-bg-surface)',
      }}
    >
      <dt style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--typography-size-sm)' }}>Saldo</dt>
      <dd style={{ margin: 0, fontSize: 'var(--typography-size-lg)', fontWeight: 'var(--typography-weight-bold)' }}>
        {formatMoneyBRL(data.saldo)}
      </dd>
    </dl>
  );
}
