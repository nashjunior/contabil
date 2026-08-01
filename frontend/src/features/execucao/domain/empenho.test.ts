/**
 * Testa o domínio puro do Empenho — sem React, sem zod, sem RHF (RAZ-202). A garantia de
 * "regra de domínio testável sem framework" só vale se o teste em si não precisar de um.
 */
import { describe, expect, it } from 'vitest';
import {
  ContratoId,
  CredorId,
  DotacaoId,
  UnidadeGestoraId,
  isDataFatoValida,
  isExercicioValido,
  isTextoObrigatorioValido,
  isTipoEmpenhoValido,
} from './empenho';

const UUID_VALIDO = '11111111-1111-4111-8111-000000000001';

describe.each([
  ['DotacaoId', DotacaoId],
  ['CredorId', CredorId],
  ['UnidadeGestoraId', UnidadeGestoraId],
  ['ContratoId', ContratoId],
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

describe('isTipoEmpenhoValido', () => {
  it('aceita ordinario/estimativo/global', () => {
    expect(isTipoEmpenhoValido('ordinario')).toBe(true);
    expect(isTipoEmpenhoValido('estimativo')).toBe(true);
    expect(isTipoEmpenhoValido('global')).toBe(true);
  });

  it('rejeita qualquer outro valor', () => {
    expect(isTipoEmpenhoValido('extraordinario')).toBe(false);
  });
});

describe('isExercicioValido', () => {
  it('aceita ano com 4 dígitos', () => {
    expect(isExercicioValido('2026')).toBe(true);
  });

  it('rejeita ano com formato diferente', () => {
    expect(isExercicioValido('26')).toBe(false);
    expect(isExercicioValido('abcd')).toBe(false);
  });
});

describe('isDataFatoValida', () => {
  it('aceita data ISO válida', () => {
    expect(isDataFatoValida('2026-01-15')).toBe(true);
  });

  it('rejeita formato fora de ISO e datas inexistentes', () => {
    expect(isDataFatoValida('15/01/2026')).toBe(false);
    expect(isDataFatoValida('2026-02-30')).toBe(false);
  });
});

describe('isTextoObrigatorioValido', () => {
  it('rejeita vazio e espaços em branco', () => {
    expect(isTextoObrigatorioValido('')).toBe(false);
    expect(isTextoObrigatorioValido('   ')).toBe(false);
  });

  it('aceita texto com conteúdo', () => {
    expect(isTextoObrigatorioValido('3.3.90.30')).toBe(true);
  });
});
