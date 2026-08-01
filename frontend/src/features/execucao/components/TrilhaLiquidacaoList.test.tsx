/**
 * RAZ-240: TIPO_LABEL tinha chaves (`liquidacao_registrada`, `liquidacao_aprovada`,
 * `liquidacao_devolvida`) que não batiam com nenhum tipo real devolvido por
 * ConsultarTrilhaLiquidacao (execucao-application) — cai sempre no fallback cru.
 * Cobre os 3 tipos reais e a derivação do rótulo de aprovação/devolução a partir de
 * `detalhes.decisao`, não de um tipo inexistente.
 */
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { EventoTrilha } from '../../../shared/api/client';
import { TrilhaLiquidacaoList } from './TrilhaLiquidacaoList';

function evento(overrides: Partial<EventoTrilha>): EventoTrilha {
  return {
    tipo: 'execucao_liquidacao_registrada',
    ator: '***.456.***-**',
    quando: '2026-07-11T00:00:00Z',
    detalhes: {},
    ...overrides,
  };
}

describe('TrilhaLiquidacaoList', () => {
  it('rotula execucao_empenho_registrado', () => {
    render(<TrilhaLiquidacaoList eventos={[evento({ tipo: 'execucao_empenho_registrado' })]} />);
    expect(screen.getByText('Empenho registrado')).toBeInTheDocument();
  });

  it('rotula execucao_liquidacao_registrada', () => {
    render(<TrilhaLiquidacaoList eventos={[evento({ tipo: 'execucao_liquidacao_registrada' })]} />);
    expect(screen.getByText('Liquidação registrada')).toBeInTheDocument();
  });

  it('deriva "Liquidação aprovada" de detalhes.decisao=APROVADA em execucao_pagamento_aprovacao_decidida', () => {
    render(
      <TrilhaLiquidacaoList
        eventos={[
          evento({
            tipo: 'execucao_pagamento_aprovacao_decidida',
            detalhes: { decisao: 'APROVADA', empenhoId: '11111111-1111-4111-8111-000000000001' },
          }),
        ]}
      />,
    );
    expect(screen.getByText('Liquidação aprovada')).toBeInTheDocument();
  });

  it('deriva "Liquidação devolvida" de detalhes.decisao=DEVOLVIDA em execucao_pagamento_aprovacao_decidida', () => {
    render(
      <TrilhaLiquidacaoList
        eventos={[
          evento({
            tipo: 'execucao_pagamento_aprovacao_decidida',
            detalhes: { decisao: 'DEVOLVIDA', empenhoId: '11111111-1111-4111-8111-000000000001' },
          }),
        ]}
      />,
    );
    expect(screen.getByText('Liquidação devolvida')).toBeInTheDocument();
  });

  it('cai no fallback do tipo cru quando decisao ausente ou tipo desconhecido', () => {
    render(
      <TrilhaLiquidacaoList
        eventos={[evento({ tipo: 'execucao_pagamento_aprovacao_decidida', detalhes: {} })]}
      />,
    );
    expect(screen.getByText('execucao_pagamento_aprovacao_decidida')).toBeInTheDocument();
  });
});
