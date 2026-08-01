/**
 * Testa o caso de uso direto — sem React, sem React Query (ADR-0041), mesmo padrão de
 * registrarEmpenho.test.ts.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../../../shared/api/mocks/server';
import type { GovbrContexto, LiquidacaoRequest } from '../../../shared/api/client';
import type { CamposLiquidacao } from '../domain/liquidacaoSchema';
import { paraLiquidacaoRequest, registrarLiquidacao } from './registrarLiquidacao';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const contexto: GovbrContexto = {
  bearerToken: 'dev.teste',
  enteId: '11111111-1111-4111-8111-000000000009',
};

const body: LiquidacaoRequest = {
  empenhoId: '11111111-1111-4111-8111-000000000010',
  dataCompetencia: '2026-01-20',
  valor: '1500.75',
  historico: 'Liquidação de material de expediente',
  documentosSuporte: [{ tipo: 'nota_fiscal', numero: 'NF-123', dataEmissao: '2026-01-18', referenciaExterna: null }],
};

describe('registrarLiquidacao', () => {
  it('registra a liquidação e preserva o valor como string decimal', async () => {
    const registro = await registrarLiquidacao(body, contexto);

    expect(registro.empenhoId).toBe(body.empenhoId);
    expect(registro.valor).toBe('1500.75');
    expect(typeof registro.valor).toBe('string');
    expect(registro.status).toBe('pendente');
  });

  it('propaga o cancelamento quando o signal já está abortado', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(registrarLiquidacao(body, contexto, { signal: controller.signal })).rejects.toBeTruthy();
  });
});

describe('paraLiquidacaoRequest', () => {
  const campos: CamposLiquidacao = {
    empenhoId: '11111111-1111-4111-8111-000000000010',
    dataCompetencia: '2026-01-20',
    valor: '1500,5',
    historico: '  Liquidação de material  ',
    documentosSuporte: [{ tipo: '  nota_fiscal  ', numero: ' NF-123 ', dataEmissao: '2026-01-18', referenciaExterna: '' }],
  };

  it('mapeia campos de formulário (string) para o LiquidacaoRequest tipado do use case', () => {
    const request = paraLiquidacaoRequest(campos);

    expect(request.valor).toBe('1500.50');
    expect(typeof request.valor).toBe('string');
    expect(request.historico).toBe('Liquidação de material');
    expect(request.documentosSuporte).toEqual([
      { tipo: 'nota_fiscal', numero: 'NF-123', dataEmissao: '2026-01-18', referenciaExterna: null },
    ]);
  });

  it('preserva referenciaExterna quando preenchida', () => {
    const request = paraLiquidacaoRequest({
      ...campos,
      documentosSuporte: [{ ...campos.documentosSuporte[0], referenciaExterna: 'contrato-42' }],
    });
    expect(request.documentosSuporte[0].referenciaExterna).toBe('contrato-42');
  });

  it('mapeia múltiplos documentos de suporte preservando a ordem', () => {
    const request = paraLiquidacaoRequest({
      ...campos,
      documentosSuporte: [
        { tipo: 'nota_fiscal', numero: 'NF-1', dataEmissao: '2026-01-10', referenciaExterna: '' },
        { tipo: 'contrato', numero: 'CT-9', dataEmissao: '2026-01-05', referenciaExterna: 'proc-7' },
      ],
    });
    expect(request.documentosSuporte).toHaveLength(2);
    expect(request.documentosSuporte[1]).toEqual({
      tipo: 'contrato',
      numero: 'CT-9',
      dataEmissao: '2026-01-05',
      referenciaExterna: 'proc-7',
    });
  });
});
