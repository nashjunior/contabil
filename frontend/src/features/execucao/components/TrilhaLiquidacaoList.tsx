import type { EventoTrilha } from '../../../shared/api/client';

const TIPO_LABEL: Record<string, string> = {
  liquidacao_registrada: 'Liquidação registrada',
  liquidacao_aprovada: 'Liquidação aprovada',
  liquidacao_devolvida: 'Liquidação devolvida',
};

export function TrilhaLiquidacaoList({ eventos }: { eventos: EventoTrilha[] }) {
  if (eventos.length === 0) {
    return <p>Nenhum evento registrado para esta liquidação.</p>;
  }

  return (
    <ol style={{ listStyle: 'none', padding: 0 }}>
      {eventos.map((evento, indice) => (
        <li key={`${evento.tipo}-${evento.quando}-${indice}`} style={{ marginBottom: 'var(--spacing-md)' }}>
          <p>
            <strong>{TIPO_LABEL[evento.tipo] ?? evento.tipo}</strong> ·{' '}
            <time dateTime={evento.quando}>{new Date(evento.quando).toLocaleString('pt-BR')}</time>
          </p>
          <p>Ator: {evento.ator}</p>
        </li>
      ))}
    </ol>
  );
}
