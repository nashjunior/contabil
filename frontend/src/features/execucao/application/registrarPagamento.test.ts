/**
 * Testa o caso de uso direto — sem React, sem React Query (ADR-0041), mesmo padrão de
 * registrarEmpenho.test.ts.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../../../shared/api/mocks/server';
import type { GovbrContexto, PagamentoRequest } from '../../../shared/api/client';
import type { CamposPagamento } from '../domain/pagamentoSchema';
import { paraPagamentoRequest, registrarPagamento } from './registrarPagamento';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const contexto: GovbrContexto = {
  bearerToken: 'dev.teste',
  enteId: '11111111-1111-4111-8111-000000000009',
};

const body: PagamentoRequest = {
  liquidacaoId: '11111111-1111-4111-8111-000000000020',
  dataCompetencia: '2026-01-25',
  valor: '1500.75',
  natureza: 'orcamentario',
  beneficiario: { nome: 'Fornecedor Exemplo Ltda', cpfCnpj: '12345678000199' },
  ordemBancaria: null,
  historico: 'Pagamento de material de expediente',
};

describe('registrarPagamento', () => {
  it('registra o pagamento e preserva o valor como string decimal', async () => {
    const registro = await registrarPagamento(body, contexto);

    expect(registro.liquidacaoId).toBe(body.liquidacaoId);
    expect(registro.valor).toBe('1500.75');
    expect(typeof registro.valor).toBe('string');
  });

  it('propaga o cancelamento quando o signal já está abortado', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(registrarPagamento(body, contexto, { signal: controller.signal })).rejects.toBeTruthy();
  });
});

describe('paraPagamentoRequest', () => {
  const campos: CamposPagamento = {
    liquidacaoId: '11111111-1111-4111-8111-000000000020',
    natureza: 'orcamentario',
    dataCompetencia: '2026-01-25',
    valor: '1500,5',
    beneficiarioNome: '  Fornecedor Exemplo Ltda  ',
    beneficiarioCpfCnpj: ' 12345678000199 ',
    ordemBancaria: '',
    historico: '  Pagamento de material  ',
  };

  it('mapeia campos de formulário (string) para o PagamentoRequest tipado do use case', () => {
    const request = paraPagamentoRequest(campos);

    expect(request.valor).toBe('1500.50');
    expect(typeof request.valor).toBe('string');
    expect(request.historico).toBe('Pagamento de material');
    expect(request.beneficiario).toEqual({ nome: 'Fornecedor Exemplo Ltda', cpfCnpj: '12345678000199' });
    expect(request.ordemBancaria).toBeNull();
  });

  it('preserva ordemBancaria quando preenchida', () => {
    const request = paraPagamentoRequest({ ...campos, ordemBancaria: ' OB-42 ' });
    expect(request.ordemBancaria).toBe('OB-42');
  });

  it('envia beneficiario nulo quando folha_consolidada sem beneficiário preenchido', () => {
    const request = paraPagamentoRequest({
      ...campos,
      natureza: 'folha_consolidada',
      beneficiarioNome: '',
      beneficiarioCpfCnpj: '',
    });
    expect(request.beneficiario).toBeUndefined();
    expect(request.natureza).toBe('folha_consolidada');
  });
});
