import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../../../shared/api/mocks/server';
import type { GovbrContexto } from '../../../shared/api/client';
import { consultarBalancete } from './consultarBalancete';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const contexto: GovbrContexto = {
  bearerToken: 'dev.teste',
  enteId: '11111111-1111-4111-8111-000000000009',
};

describe('consultarBalancete', () => {
  it('resolve o balancete do período com Σdébito = Σcrédito (razão append-only)', async () => {
    const balancete = await consultarBalancete(2026, 1, contexto);

    expect(balancete.exercicio).toBe(2026);
    expect(balancete.mes).toBe(1);
    expect(balancete.linhas.length).toBeGreaterThan(0);
    expect(balancete.totalMovimentoDebito).toBe(balancete.totalMovimentoCredito);
    expect(balancete.confere).toBe(true);
  });

  it('propaga o cancelamento quando o signal já está abortado', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(
      consultarBalancete(2026, 1, contexto, { signal: controller.signal }),
    ).rejects.toBeTruthy();
  });
});
