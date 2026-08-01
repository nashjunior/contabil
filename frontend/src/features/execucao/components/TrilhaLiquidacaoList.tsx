import type { EventoTrilha } from '../../../shared/api/client';

/** Tipos reais devolvidos por `ConsultarTrilhaLiquidacao` (execucao-application). */
const TIPO_APROVACAO_DECIDIDA = 'execucao_pagamento_aprovacao_decidida';

const TIPO_LABEL: Record<string, string> = {
  execucao_empenho_registrado: 'Empenho registrado',
  execucao_liquidacao_registrada: 'Liquidação registrada',
};

/** `detalhes.decisao` vem de `StatusAprovacao#name()` no backend (AprovarPagamento). */
const DECISAO_LABEL: Record<string, string> = {
  APROVADA: 'Liquidação aprovada',
  DEVOLVIDA: 'Liquidação devolvida',
};

function rotulo(evento: EventoTrilha): string {
  if (evento.tipo === TIPO_APROVACAO_DECIDIDA) {
    const decisao = evento.detalhes.decisao;
    return (decisao && DECISAO_LABEL[decisao]) ?? evento.tipo;
  }
  return TIPO_LABEL[evento.tipo] ?? evento.tipo;
}

export function TrilhaLiquidacaoList({ eventos }: { eventos: EventoTrilha[] }) {
  if (eventos.length === 0) {
    return <p>Nenhum evento registrado para esta liquidação.</p>;
  }

  return (
    <ol style={{ listStyle: 'none', padding: 0 }}>
      {eventos.map((evento, indice) => (
        <li key={`${evento.tipo}-${evento.quando}-${indice}`} style={{ marginBottom: 'var(--spacing-md)' }}>
          <p>
            <strong>{rotulo(evento)}</strong> ·{' '}
            <time dateTime={evento.quando}>{new Date(evento.quando).toLocaleString('pt-BR')}</time>
          </p>
          <p>Ator: {evento.ator}</p>
        </li>
      ))}
    </ol>
  );
}
