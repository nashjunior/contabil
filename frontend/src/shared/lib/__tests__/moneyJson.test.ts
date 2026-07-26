import { describe, expect, it } from 'vitest';
import { parseJsonPreservingMoney } from '../moneyJson';

describe('parseJsonPreservingMoney', () => {
  it('preserva o campo valor como string mesmo vindo como numero JSON', () => {
    const raw = '{"tipo":"EMPENHO","id":"a1","numero":"1","valor":1000.50,"status":"REGISTRADO"}';
    const parsed = parseJsonPreservingMoney(raw) as Record<string, unknown>;
    expect(parsed.valor).toBe('1000.50');
    expect(typeof parsed.valor).toBe('string');
  });

  it('nao quebra quando valor ja vem como string', () => {
    const raw = '{"valor":"1000.50"}';
    const parsed = parseJsonPreservingMoney(raw) as Record<string, unknown>;
    expect(parsed.valor).toBe('1000.50');
  });

  it('preserva escala exata (nao arredonda, nao perde zero a direita)', () => {
    const raw = '{"valor":10000000000.10}';
    const parsed = parseJsonPreservingMoney(raw) as Record<string, unknown>;
    expect(parsed.valor).toBe('10000000000.10');
  });
});
