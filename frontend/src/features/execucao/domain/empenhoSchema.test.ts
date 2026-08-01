/**
 * Testa que o schema (fronteira de validação do formulário) deriva do domínio, sem
 * reimplementar regra própria — sem React/RHF (RAZ-202).
 */
import { describe, expect, it } from 'vitest';
import { CAMPOS_EMPENHO_VAZIOS, empenhoSchema } from './empenhoSchema';

const CAMPOS_VALIDOS = {
  dotacaoId: '11111111-1111-4111-8111-000000000001',
  tipo: 'ordinario' as const,
  credorId: '11111111-1111-4111-8111-000000000002',
  unidadeGestoraId: '11111111-1111-4111-8111-000000000003',
  contratoId: '',
  valor: '1000.50',
  dataFato: '2026-01-15',
  exercicio: '2026',
  classificacaoOrcamentaria: '3.3.90.30',
  fonteRecurso: '0100',
  historico: 'Compra de material de expediente',
};

describe('empenhoSchema', () => {
  it('aceita o conjunto de campos válido', () => {
    expect(empenhoSchema.safeParse(CAMPOS_VALIDOS).success).toBe(true);
  });

  it('os valores vazios iniciais nunca satisfazem o schema (formulário começa inválido)', () => {
    expect(empenhoSchema.safeParse(CAMPOS_EMPENHO_VAZIOS).success).toBe(false);
  });

  it.each([
    ['dotacaoId', 'não-é-uuid', 'Informe um UUID válido.'],
    ['credorId', 'não-é-uuid', 'Informe um UUID válido.'],
    ['unidadeGestoraId', 'não-é-uuid', 'Informe um UUID válido.'],
    ['valor', 'abc', 'Informe um valor decimal válido (ex.: 1000.00).'],
    ['dataFato', '15/01/2026', 'Informe a data do fato.'],
    ['exercicio', '26', 'Informe o exercício (aaaa).'],
    ['classificacaoOrcamentaria', '   ', 'Informe a classificação orçamentária.'],
    ['fonteRecurso', '   ', 'Informe a fonte de recurso.'],
    ['historico', '   ', 'Informe o histórico.'],
  ])('rejeita %s inválido com a mensagem derivada do domínio', (campo, valor, mensagem) => {
    const resultado = empenhoSchema.safeParse({ ...CAMPOS_VALIDOS, [campo]: valor });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.some((i) => i.message === mensagem)).toBe(true);
    }
  });

  it('dotacaoId vazio (nada escolhido no picker) tem mensagem própria, distinta de UUID malformado', () => {
    const resultado = empenhoSchema.safeParse({ ...CAMPOS_VALIDOS, dotacaoId: '' });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.map((i) => i.message)).toEqual(['Selecione uma dotação.']);
    }
  });

  it('contratoId vazio é válido (campo opcional)', () => {
    expect(empenhoSchema.safeParse({ ...CAMPOS_VALIDOS, contratoId: '' }).success).toBe(true);
  });

  it('contratoId preenchido precisa ser um UUID válido', () => {
    expect(empenhoSchema.safeParse({ ...CAMPOS_VALIDOS, contratoId: 'inválido' }).success).toBe(false);
    expect(
      empenhoSchema.safeParse({ ...CAMPOS_VALIDOS, contratoId: '11111111-1111-4111-8111-000000000004' }).success,
    ).toBe(true);
  });

  it('valor aceita vírgula decimal (mesma normalização do formulário antigo)', () => {
    expect(empenhoSchema.safeParse({ ...CAMPOS_VALIDOS, valor: '1000,50' }).success).toBe(true);
  });

  it('rejeita tipo fora do enum do domínio', () => {
    expect(empenhoSchema.safeParse({ ...CAMPOS_VALIDOS, tipo: 'extraordinario' }).success).toBe(false);
  });
});
