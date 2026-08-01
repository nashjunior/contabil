import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../../../shared/api/mocks/server';
import type { GovbrContexto } from '../../../shared/api/client';
import { consultarExecucaoOrcamentaria } from './consultarExecucaoOrcamentaria';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const contexto: GovbrContexto = {
  bearerToken: 'dev.teste',
  enteId: '11111111-1111-4111-8111-000000000009',
};

describe('consultarExecucaoOrcamentaria', () => {
  it('resolve o agregado do período', async () => {
    const agregado = await consultarExecucaoOrcamentaria(2026, 1, contexto);

    expect(agregado.exercicio).toBe(2026);
    expect(typeof agregado.totalEmpenhado).toBe('string');
  });

  it('propaga o cancelamento quando o signal já está abortado', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(
      consultarExecucaoOrcamentaria(2026, 1, contexto, { signal: controller.signal }),
    ).rejects.toBeTruthy();
  });
});
