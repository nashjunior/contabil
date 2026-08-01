import type { ContaResumo } from '../../../shared/api/client';
import { depthFromCodigo } from '../lib/depthFromCodigo';
import { StatusPill } from './StatusPill';

/**
 * Tela 4 — Catálogo de Contas PCASP (Figma página 12/RAZ-142): navegação/consulta do plano
 * de contas. Lista PLANA do backend (ADR-0030 §6), indentação hierárquica calculada
 * client-side por `depthFromCodigo` — mesmo cálculo do `ContaPicker`.
 */
export function ContasCatalogoTable({ itens }: { itens: ContaResumo[] }) {
  if (itens.length === 0) {
    return <p>Nenhuma conta encontrada.</p>;
  }

  return (
    <table>
      <caption className="sr-only">Catálogo de contas PCASP</caption>
      <thead>
        <tr>
          <th scope="col">Código</th>
          <th scope="col">Descrição</th>
          <th scope="col">Natureza</th>
          <th scope="col">Escrituração</th>
        </tr>
      </thead>
      <tbody>
        {itens.map((conta) => (
          <tr key={conta.id}>
            <td>{conta.codigo}</td>
            <td style={{ paddingInlineStart: `calc(var(--spacing-lg) * ${depthFromCodigo(conta.codigo)})` }}>
              {conta.descricao}
            </td>
            <td>{conta.naturezaSaldo === 'D' ? 'Devedora' : 'Credora'}</td>
            <td>
              <StatusPill tone={conta.escrituravel ? 'success' : 'neutral'}>
                {conta.escrituravel ? 'Analítica' : 'Sintética'}
              </StatusPill>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
