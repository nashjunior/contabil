/**
 * Testa o domínio puro da Liquidação — sem React, sem zod, sem RHF (RAZ-230, mesmo padrão de
 * `empenho.test.ts`, RAZ-202).
 */
import { describe, expect, it } from 'vitest';
import { EmpenhoId, LiquidacaoId, isTextoObrigatorioValido } from './liquidacao';

const UUID_VALIDO = '11111111-1111-4111-8111-000000000001';

describe.each([
  ['EmpenhoId', EmpenhoId],
  ['LiquidacaoId', LiquidacaoId],
])('%s', (_nome, vo) => {
  it('aceita um UUID válido', () => {
    expect(vo.isValid(UUID_VALIDO)).toBe(true);
    expect(vo.parse(UUID_VALIDO)).toBe(UUID_VALIDO);
  });

  it('rejeita texto que não é UUID', () => {
    expect(vo.isValid('não-é-uuid')).toBe(false);
    expect(() => vo.parse('não-é-uuid')).toThrow();
  });
});

describe('isTextoObrigatorioValido', () => {
  it('rejeita vazio e espaços em branco', () => {
    expect(isTextoObrigatorioValido('')).toBe(false);
    expect(isTextoObrigatorioValido('   ')).toBe(false);
  });

  it('aceita texto com conteúdo', () => {
    expect(isTextoObrigatorioValido('Nota fiscal 123')).toBe(true);
  });
});
