/**
 * Testa o caso de uso direto — sem React, sem React Query (ADR-0041): a
 * garantia de "testável sem framework" só vale se o teste em si não precisar
 * de framework para exercitá-lo.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../../../shared/api/mocks/server';
import { ApiError, type GovbrContexto } from '../../../shared/api/client';
import { consultarSaldo } from './consultarSaldo';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const contexto: GovbrContexto = {
  bearerToken: 'dev.teste',
  enteId: '11111111-1111-4111-8111-000000000009',
};

const CONTA_CAIXA = '22222222-2222-4222-8222-000000000003';

describe('consultarSaldo', () => {
  it('resolve o saldo da conta como string decimal', async () => {
    const resposta = await consultarSaldo(CONTA_CAIXA, contexto);

    expect(resposta.contaId).toBe(CONTA_CAIXA);
    expect(typeof resposta.saldo).toBe('string');
  });

  it('propaga conta_nao_encontrada (404) para conta inexistente', async () => {
    await expect(consultarSaldo('00000000-0000-4000-8000-000000000000', contexto)).rejects.toMatchObject({
      status: 404,
      codigo: 'conta_nao_encontrada',
    } satisfies Partial<ApiError>);
  });

  it('propaga o cancelamento quando o signal já está abortado', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(consultarSaldo(CONTA_CAIXA, contexto, { signal: controller.signal })).rejects.toBeTruthy();
  });
});
