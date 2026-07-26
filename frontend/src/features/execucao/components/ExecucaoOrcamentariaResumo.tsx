import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import { useExecucaoOrcamentaria } from '../api/useExecucaoOrcamentaria';

/** Consulta REAL (GET /execucao/orcamentaria) — prova fim-a-fim contra um endpoint de leitura real. */
export function ExecucaoOrcamentariaResumo({ exercicio, mes }: { exercicio: number; mes: number }) {
  const { data, isLoading, isError, error } = useExecucaoOrcamentaria(exercicio, mes);

  if (isLoading) return <p role="status">Consultando execução orçamentária…</p>;

  if (isError) {
    return (
      <p role="alert" style={{ color: 'var(--color-state-danger-fg)' }}>
        Não foi possível consultar a execução orçamentária: {error.message}
      </p>
    );
  }

  if (!data) return null;

  const linhas: Array<[string, string]> = [
    ['Total empenhado', formatMoneyBRL(data.totalEmpenhado)],
    ['Total liquidado', formatMoneyBRL(data.totalLiquidado)],
    ['Total pago', formatMoneyBRL(data.totalPago)],
    ['Saldo a liquidar', formatMoneyBRL(data.saldoALiquidar)],
    ['Saldo a pagar', formatMoneyBRL(data.saldoAPagar)],
  ];

  return (
    <dl style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 'var(--spacing-lg)' }}>
      {linhas.map(([rotulo, valor]) => (
        <div key={rotulo}>
          <dt style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--typography-size-sm)' }}>{rotulo}</dt>
          <dd style={{ margin: 0, fontSize: 'var(--typography-size-lg)', fontWeight: 'var(--typography-weight-bold)' }}>{valor}</dd>
        </div>
      ))}
    </dl>
  );
}
