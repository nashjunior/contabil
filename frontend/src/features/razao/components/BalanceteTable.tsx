import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import type { BalanceteResponse } from '../../../shared/api/client';
import { StatusPill } from './StatusPill';

/**
 * Tabela — Balancete (Figma página 11/RAZ-112): colunas Código/Descrição/Saldo anterior/
 * Mov. Débito/Mov. Crédito/Saldo atual, rodapé com totais e o indicador "Confere"
 * (Σdébito = Σcrédito do período, materializa `BalanceteResponse.confere` — razão
 * append-only, Σ=Σ).
 */
export function BalanceteTable({ balancete }: { balancete: BalanceteResponse }) {
  const { linhas, totalMovimentoDebito, totalMovimentoCredito, confere } = balancete;

  if (linhas.length === 0) {
    return <p>Nenhum lançamento no período.</p>;
  }

  return (
    <>
      <table>
        <caption className="sr-only">Balancete do período</caption>
        <thead>
          <tr>
            <th scope="col">Código</th>
            <th scope="col">Descrição</th>
            <th scope="col">Saldo anterior</th>
            <th scope="col">Mov. Débito</th>
            <th scope="col">Mov. Crédito</th>
            <th scope="col">Saldo atual</th>
          </tr>
        </thead>
        <tbody>
          {linhas.map((linha) => (
            <tr key={linha.contaId}>
              <td>{linha.codigo}</td>
              <td>{linha.descricao}</td>
              <td>{formatMoneyBRL(linha.saldoAnterior)}</td>
              <td>{formatMoneyBRL(linha.movimentoDebito)}</td>
              <td>{formatMoneyBRL(linha.movimentoCredito)}</td>
              <td>{formatMoneyBRL(linha.saldoAtual)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row" colSpan={3}>
              Totais do período
            </th>
            <td>{formatMoneyBRL(totalMovimentoDebito)}</td>
            <td>{formatMoneyBRL(totalMovimentoCredito)}</td>
            <td />
          </tr>
        </tfoot>
      </table>
      <p style={{ marginTop: 'var(--spacing-md)' }}>
        <StatusPill tone={confere ? 'success' : 'danger'}>
          {confere ? 'Confere — Σdébito = Σcrédito' : 'Não confere — Σdébito ≠ Σcrédito'}
        </StatusPill>
      </p>
    </>
  );
}
