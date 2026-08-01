import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { server } from '../../../shared/api/mocks/server';
import type { GovbrContexto } from '../../../shared/api/client';
import { consultarContas } from './consultarContas';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const contexto: GovbrContexto = {
  bearerToken: 'dev.teste',
  enteId: '11111111-1111-4111-8111-000000000009',
};

describe('consultarContas', () => {
  it('resolve a primeira página do catálogo (cursor opaco)', async () => {
    const pagina = await consultarContas({ limit: 2 }, contexto);

    expect(pagina.itens).toHaveLength(2);
    expect(typeof pagina.proximoCursor === 'string' || pagina.proximoCursor === null).toBe(true);
  });

  it('filtra por prefixo de código via busca', async () => {
    const pagina = await consultarContas({ busca: '1.1' }, contexto);

    expect(pagina.itens.every((item) => item.codigo.startsWith('1.1'))).toBe(true);
  });

  it('propaga o cancelamento quando o signal já está abortado', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(consultarContas({}, contexto, { signal: controller.signal })).rejects.toBeTruthy();
  });
});
