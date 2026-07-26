import { describe, expect, it } from 'vitest';
import { formatMoneyBRL, isValidMoney, toMoney } from '../dinheiro';

describe('dinheiro', () => {
  it('valida decimais com ate 2 casas', () => {
    expect(isValidMoney('1000.00')).toBe(true);
    expect(isValidMoney('1000')).toBe(true);
    expect(isValidMoney('1000.5')).toBe(true);
    expect(isValidMoney('1000.555')).toBe(false);
    expect(isValidMoney('abc')).toBe(false);
  });

  it('normaliza para escala 2', () => {
    expect(toMoney('1000')).toBe('1000.00');
    expect(toMoney('1000,5')).toBe('1000.50');
    expect(toMoney('1000.5')).toBe('1000.50');
  });

  it('rejeita valor invalido', () => {
    expect(() => toMoney('abc')).toThrow();
  });

  it('formata em R$ pt-BR', () => {
    expect(formatMoneyBRL(toMoney('1234.5'))).toBe('R$ 1.234,50');
  });
});
