import { formatMoneyBRL } from '../../../shared/lib/dinheiro';
import type { EmpenhoResponse } from '../../../shared/api/client';

const TIPO_LABEL: Record<string, string> = {
  ordinario: 'Ordinário',
  estimativo: 'Estimativo',
  global: 'Global',
};

export function ExecucaoList({ itens }: { itens: EmpenhoResponse[] }) {
  if (itens.length === 0) {
    return <p>Nenhum empenho registrado nesta sessão ainda.</p>;
  }

  return (
    <table>
      <caption className="sr-only">Empenhos registrados nesta sessão</caption>
      <thead>
        <tr>
          <th scope="col">Nº sequencial</th>
          <th scope="col">Tipo</th>
          <th scope="col">Valor</th>
          <th scope="col">Data do fato</th>
          <th scope="col">Histórico</th>
        </tr>
      </thead>
      <tbody>
        {itens.map((item) => (
          <tr key={item.id}>
            <td>
              {item.exercicio}/{item.numeroSequencial}
            </td>
            <td>{TIPO_LABEL[item.tipo] ?? item.tipo}</td>
            <td>{formatMoneyBRL(item.valor)}</td>
            <td>
              <time dateTime={item.dataFato}>{new Date(`${item.dataFato}T00:00:00`).toLocaleDateString('pt-BR')}</time>
            </td>
            <td>{item.historico}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
