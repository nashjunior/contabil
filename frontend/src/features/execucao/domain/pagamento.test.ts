/**
 * Testa o domínio puro do Pagamento — sem React, sem zod, sem RHF (RAZ-230, mesmo padrão de
 * `empenho.test.ts`, RAZ-202).
 */
import { describe, expect, it } from 'vitest';
import { LiquidacaoId, TIPOS_NATUREZA_PAGAMENTO, isNaturezaPagamentoValida, isTextoObrigatorioValido } from './pagamento';

const UUID_VALIDO = '11111111-1111-4111-8111-000000000001';

describe('LiquidacaoId', () => {
  it('aceita um UUID válido', () => {
    expect(LiquidacaoId.isValid(UUID_VALIDO)).toBe(true);
    expect(LiquidacaoId.parse(UUID_VALIDO)).toBe(UUID_VALIDO);
  });

  it('rejeita texto que não é UUID', () => {
    expect(LiquidacaoId.isValid('não-é-uuid')).toBe(false);
    expect(() => LiquidacaoId.parse('não-é-uuid')).toThrow();
  });
});

describe('isNaturezaPagamentoValida', () => {
  it('aceita orcamentario/folha_consolidada', () => {
    for (const natureza of TIPOS_NATUREZA_PAGAMENTO) {
      expect(isNaturezaPagamentoValida(natureza)).toBe(true);
    }
  });

  it('rejeita qualquer outro valor', () => {
    expect(isNaturezaPagamentoValida('extraordinario')).toBe(false);
  });
});

describe('isTextoObrigatorioValido', () => {
  it('rejeita vazio e espaços em branco', () => {
    expect(isTextoObrigatorioValido('')).toBe(false);
    expect(isTextoObrigatorioValido('   ')).toBe(false);
  });

  it('aceita texto com conteúdo', () => {
    expect(isTextoObrigatorioValido('Ordem bancária 42')).toBe(true);
  });
});
