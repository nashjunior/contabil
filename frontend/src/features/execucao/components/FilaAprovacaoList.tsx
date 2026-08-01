import { Link } from 'react-router-dom';
import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import type { ItemFilaAprovacao } from '../../../shared/api/client';

const STATUS_LABEL: Record<string, string> = {
  pendente: 'Pendente',
  aprovada: 'Aprovada',
  devolvida: 'Devolvida',
};

export function FilaAprovacaoList({ itens }: { itens: ItemFilaAprovacao[] }) {
  if (itens.length === 0) {
    return <p>Nenhuma liquidação nesta fila.</p>;
  }

  return (
    <table>
      <caption className="sr-only">Fila de aprovação de liquidações</caption>
      <thead>
        <tr>
          <th scope="col">Empenho</th>
          <th scope="col">Valor</th>
          <th scope="col">Data de competência</th>
          <th scope="col">Status</th>
          <th scope="col">Trilha</th>
        </tr>
      </thead>
      <tbody>
        {itens.map((item) => (
          <tr key={item.id}>
            <td>
              {item.exercicioEmpenho}/{item.numeroEmpenho}
            </td>
            <td>{formatMoneyBRL(item.valor)}</td>
            <td>
              <time dateTime={item.dataCompetencia}>
                {new Date(`${item.dataCompetencia}T00:00:00`).toLocaleDateString('pt-BR')}
              </time>
            </td>
            <td>{STATUS_LABEL[item.statusAprovacao] ?? item.statusAprovacao}</td>
            <td>
              <Link to={`/execucao/liquidacoes/${item.id}/trilha`}>Ver trilha</Link>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
