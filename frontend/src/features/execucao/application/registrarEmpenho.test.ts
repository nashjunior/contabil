/**
 * Testa o caso de uso direto — sem React, sem React Query (ADR-0041): a
 * garantia de "testável sem framework" só vale se o teste em si não precisar
 * de framework para exercitá-lo.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../../../shared/api/mocks/server';
import type { EmpenhoRequest, GovbrContexto } from '../../../shared/api/client';
import { registrarEmpenho } from './registrarEmpenho';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const contexto: GovbrContexto = {
  bearerToken: 'dev.teste',
  enteId: '11111111-1111-4111-8111-000000000009',
};

const body: EmpenhoRequest = {
  dotacaoId: '11111111-1111-4111-8111-000000000001',
  tipo: 'ordinario',
  credorId: '11111111-1111-4111-8111-000000000002',
  unidadeGestoraId: '11111111-1111-4111-8111-000000000003',
  valor: '1000.75',
  dataFato: '2026-01-15',
  exercicio: 2026,
  classificacaoOrcamentaria: '3.3.90.30',
  fonteRecurso: '0100',
  historico: 'Compra de material de expediente',
};

describe('registrarEmpenho', () => {
  it('registra o empenho e preserva o valor como string decimal', async () => {
    const registro = await registrarEmpenho(body, contexto);

    expect(registro.dotacaoId).toBe(body.dotacaoId);
    expect(registro.valor).toBe('1000.75');
    expect(typeof registro.valor).toBe('string');
  });

  it('propaga o cancelamento quando o signal já está abortado', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(registrarEmpenho(body, contexto, { signal: controller.signal })).rejects.toBeTruthy();
  });
});
