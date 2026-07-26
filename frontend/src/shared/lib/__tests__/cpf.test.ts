import { describe, expect, it } from 'vitest';
import { maskCpf } from '../cpf';

describe('maskCpf', () => {
  it('mascara mantendo so o bloco do meio', () => {
    expect(maskCpf('12345678900')).toBe('***.456.***-**');
  });

  it('aceita CPF com pontuacao e mascara igual', () => {
    expect(maskCpf('123.456.789-00')).toBe('***.456.***-**');
  });

  it('rejeita CPF com tamanho invalido', () => {
    expect(() => maskCpf('123')).toThrow();
  });
});
