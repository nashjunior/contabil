import { describe, expect, it } from 'vitest';
import { formatMoneyBRL, isValidMoney, somarMoney, subtrairMoney, toMoney } from '../dinheiro';

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

  it('soma decimais sem erro de ponto flutuante', () => {
    // 0.1 + 0.2 em float da 0.30000000000000004 — a soma decimal exata deve dar exato.
    expect(somarMoney('0.10', '0.20')).toBe('0.30');
    expect(somarMoney('1000.55', '2000.45', '0.01')).toBe('3001.01');
    expect(somarMoney()).toBe('0.00');
  });

  it('subtrai decimais exatamente, inclusive resultado negativo', () => {
    expect(subtrairMoney('100.00', '30.25')).toBe('69.75');
    expect(subtrairMoney('10.00', '25.50')).toBe('-15.50');
  });

  it('formata em R$ pt-BR', () => {
    expect(formatMoneyBRL(toMoney('1234.5'))).toBe('R$ 1.234,50');
  });
});
