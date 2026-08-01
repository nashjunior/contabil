/**
 * Testa o caso de uso direto e o mapeamento de erro (ADR-0055, decisão 4) — sem React, sem
 * React Query (ADR-0041), mesmo padrão de registrarLiquidacao.test.ts.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../../../shared/api/mocks/server';
import { ApiError, type GovbrContexto } from '../../../shared/api/client';
import type { CamposDecisaoAprovacao } from '../domain/decisaoAprovacaoSchema';
import {
  aprovarLiquidacao,
  CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA,
  mensagemAmigavelDecisao,
  paraAprovacaoRequest,
} from './aprovarLiquidacao';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const contexto: GovbrContexto = {
  bearerToken: 'dev.teste',
  enteId: '11111111-1111-4111-8111-000000000009',
};

const LIQUIDACAO_ID = '44444444-4444-4444-8444-000000000001';

describe('aprovarLiquidacao', () => {
  it('envia a decisão e devolve a liquidação atualizada', async () => {
    server.use(
      http.post('/api/v1/entes/:enteId/execucao/liquidacoes/:id/aprovacao', () =>
        HttpResponse.json({ id: LIQUIDACAO_ID, status: 'aprovada' }, { status: 200 }),
      ),
    );

    const resultado = await aprovarLiquidacao(LIQUIDACAO_ID, { decisao: 'aprovar' }, contexto);
    expect(resultado.status).toBe('aprovada');
  });
});

describe('paraAprovacaoRequest', () => {
  it('aprovar nunca envia motivo, mesmo se o campo tiver texto residual', () => {
    const campos: CamposDecisaoAprovacao = { decisao: 'aprovar', motivo: 'texto que não deveria ir' };
    expect(paraAprovacaoRequest(campos)).toEqual({ decisao: 'aprovar', motivo: undefined });
  });

  it('devolver envia o motivo já sem espaços nas pontas', () => {
    const campos: CamposDecisaoAprovacao = { decisao: 'devolver', motivo: '  documento divergente  ' };
    expect(paraAprovacaoRequest(campos)).toEqual({ decisao: 'devolver', motivo: 'documento divergente' });
  });
});

describe('mensagemAmigavelDecisao', () => {
  it.each([
    ['auto_aprovacao_vedada', /segregação de funções/],
    ['liquidacao_ja_decidida', /já foi decidida por outro usuário/],
    ['sem_permissao', /papel AUTORIZADOR necessário/],
    ['mfa_requerido', /verificação adicional de segurança/],
    ['motivo_devolucao_obrigatorio', /Informe o motivo da devolução\./],
    ['liquidacao_nao_encontrada', /fila foi atualizada/],
    ['empenho_nao_encontrado', /fila foi atualizada/],
  ])('traduz o código %s pra mensagem amigável', (codigo, esperado) => {
    const erro = new ApiError(409, codigo, 'mensagem crua do backend');
    expect(mensagemAmigavelDecisao(erro)).toMatch(esperado);
  });

  it('cai no erro.message cru quando o código não está mapeado', () => {
    const erro = new ApiError(500, 'erro_desconhecido', 'algo quebrou no servidor');
    expect(mensagemAmigavelDecisao(erro)).toBe('algo quebrou no servidor');
  });

  it('usa mensagem genérica quando o erro não é um ApiError (ex.: falha de rede)', () => {
    expect(mensagemAmigavelDecisao(new TypeError('network error'))).toBe('Não foi possível concluir a decisão. Tente novamente.');
  });
});

describe('CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA', () => {
  it('cobre exatamente os códigos em que o item deixou de ser decidível', () => {
    expect([...CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA].sort()).toEqual(
      ['empenho_nao_encontrado', 'liquidacao_ja_decidida', 'liquidacao_nao_encontrada'].sort(),
    );
  });

  it('não inclui códigos que mantêm o item na fila (auto-aprovação, permissão, MFA, motivo)', () => {
    for (const codigo of ['auto_aprovacao_vedada', 'sem_permissao', 'mfa_requerido', 'motivo_devolucao_obrigatorio']) {
      expect(CODIGOS_ERRO_QUE_REMOVEM_ITEM_DA_FILA.has(codigo)).toBe(false);
    }
  });
});
