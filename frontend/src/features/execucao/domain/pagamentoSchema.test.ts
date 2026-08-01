/**
 * Testa que o schema (fronteira de validação do formulário) deriva do domínio, sem
 * reimplementar regra própria — sem React/RHF (RAZ-230, mesmo padrão de empenhoSchema.test.ts).
 */
import { describe, expect, it } from 'vitest';
import { CAMPOS_PAGAMENTO_VAZIOS, pagamentoSchema } from './pagamentoSchema';

const CAMPOS_VALIDOS = {
  liquidacaoId: '11111111-1111-4111-8111-000000000020',
  natureza: 'orcamentario' as const,
  dataCompetencia: '2026-01-25',
  valor: '1500.00',
  beneficiarioNome: 'Fornecedor Exemplo Ltda',
  beneficiarioCpfCnpj: '12345678000199',
  ordemBancaria: '',
  historico: 'Pagamento de material de expediente',
};

describe('pagamentoSchema', () => {
  it('aceita o conjunto de campos válido', () => {
    expect(pagamentoSchema.safeParse(CAMPOS_VALIDOS).success).toBe(true);
  });

  it('os valores vazios iniciais nunca satisfazem o schema (formulário começa inválido)', () => {
    expect(pagamentoSchema.safeParse(CAMPOS_PAGAMENTO_VAZIOS).success).toBe(false);
  });

  it('liquidacaoId vazio (nada escolhido no picker) tem mensagem própria, distinta de UUID malformado', () => {
    const resultado = pagamentoSchema.safeParse({ ...CAMPOS_VALIDOS, liquidacaoId: '' });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.map((i) => i.message)).toEqual(['Selecione uma liquidação aprovada.']);
    }
  });

  it('liquidacaoId preenchido com texto que não é UUID é rejeitado', () => {
    const resultado = pagamentoSchema.safeParse({ ...CAMPOS_VALIDOS, liquidacaoId: 'não-é-uuid' });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.some((i) => i.message === 'Informe um UUID válido.')).toBe(true);
    }
  });

  it('rejeita natureza fora do domínio', () => {
    expect(pagamentoSchema.safeParse({ ...CAMPOS_VALIDOS, natureza: 'extraordinario' }).success).toBe(false);
  });

  it.each([
    ['valor', 'abc', 'Informe um valor decimal válido (ex.: 1000.00).'],
    ['dataCompetencia', '25/01/2026', 'Informe a data de competência.'],
    ['historico', '   ', 'Informe o histórico.'],
  ])('rejeita %s inválido com a mensagem derivada do domínio', (campo, valor, mensagem) => {
    const resultado = pagamentoSchema.safeParse({ ...CAMPOS_VALIDOS, [campo]: valor });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.some((i) => i.message === mensagem)).toBe(true);
    }
  });

  it('beneficiário nominal é obrigatório para pagamento orçamentário', () => {
    const resultado = pagamentoSchema.safeParse({ ...CAMPOS_VALIDOS, beneficiarioNome: '', beneficiarioCpfCnpj: '' });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      const mensagens = resultado.error.issues.map((i) => i.message);
      expect(mensagens).toEqual(
        expect.arrayContaining(['Informe o nome do beneficiário.', 'Informe o CPF/CNPJ do beneficiário.']),
      );
    }
  });

  it('beneficiário nominal é opcional para folha consolidada', () => {
    const resultado = pagamentoSchema.safeParse({
      ...CAMPOS_VALIDOS,
      natureza: 'folha_consolidada',
      beneficiarioNome: '',
      beneficiarioCpfCnpj: '',
    });
    expect(resultado.success).toBe(true);
  });
});
