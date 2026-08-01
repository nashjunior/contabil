import { describe, expect, it } from 'vitest';
import { isDataIsoValida } from '../data';

describe('isDataIsoValida', () => {
  it('aceita data ISO válida', () => {
    expect(isDataIsoValida('2026-01-15')).toBe(true);
  });

  it('rejeita formato fora de ISO e datas inexistentes', () => {
    expect(isDataIsoValida('15/01/2026')).toBe(false);
    expect(isDataIsoValida('2026-02-30')).toBe(false);
    expect(isDataIsoValida('abcd-ef-gh')).toBe(false);
  });

  it('ignora espaços nas bordas', () => {
    expect(isDataIsoValida('  2026-01-15  ')).toBe(true);
  });
});
