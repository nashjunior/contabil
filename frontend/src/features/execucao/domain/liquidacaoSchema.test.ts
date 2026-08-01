/**
 * Testa que o schema (fronteira de validação do formulário) deriva do domínio, sem
 * reimplementar regra própria — sem React/RHF (RAZ-230, mesmo padrão de empenhoSchema.test.ts).
 */
import { describe, expect, it } from 'vitest';
import { CAMPOS_LIQUIDACAO_VAZIOS, liquidacaoSchema } from './liquidacaoSchema';

const CAMPOS_VALIDOS = {
  empenhoId: '11111111-1111-4111-8111-000000000010',
  dataCompetencia: '2026-01-20',
  valor: '1500.00',
  historico: 'Liquidação de material de expediente',
  documentosSuporte: [{ tipo: 'nota_fiscal', numero: 'NF-123', dataEmissao: '2026-01-18', referenciaExterna: '' }],
};

describe('liquidacaoSchema', () => {
  it('aceita o conjunto de campos válido', () => {
    expect(liquidacaoSchema.safeParse(CAMPOS_VALIDOS).success).toBe(true);
  });

  it('os valores vazios iniciais nunca satisfazem o schema (formulário começa inválido)', () => {
    expect(liquidacaoSchema.safeParse(CAMPOS_LIQUIDACAO_VAZIOS).success).toBe(false);
  });

  it('empenhoId vazio (nada escolhido no picker) tem mensagem própria, distinta de UUID malformado', () => {
    const resultado = liquidacaoSchema.safeParse({ ...CAMPOS_VALIDOS, empenhoId: '' });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.map((i) => i.message)).toEqual(['Selecione um empenho.']);
    }
  });

  it('empenhoId preenchido com texto que não é UUID é rejeitado', () => {
    const resultado = liquidacaoSchema.safeParse({ ...CAMPOS_VALIDOS, empenhoId: 'não-é-uuid' });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.some((i) => i.message === 'Informe um UUID válido.')).toBe(true);
    }
  });

  it.each([
    ['valor', 'abc', 'Informe um valor decimal válido (ex.: 1000.00).'],
    ['dataCompetencia', '15/01/2026', 'Informe a data de competência.'],
    ['historico', '   ', 'Informe o histórico.'],
  ])('rejeita %s inválido com a mensagem derivada do domínio', (campo, valor, mensagem) => {
    const resultado = liquidacaoSchema.safeParse({ ...CAMPOS_VALIDOS, [campo]: valor });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.some((i) => i.message === mensagem)).toBe(true);
    }
  });

  it('valor aceita vírgula decimal', () => {
    expect(liquidacaoSchema.safeParse({ ...CAMPOS_VALIDOS, valor: '1500,00' }).success).toBe(true);
  });

  it('exige ao menos um documento de suporte', () => {
    const resultado = liquidacaoSchema.safeParse({ ...CAMPOS_VALIDOS, documentosSuporte: [] });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues.some((i) => i.message === 'Inclua ao menos um documento de suporte.')).toBe(true);
    }
  });

  it('rejeita documento de suporte com campos obrigatórios em branco', () => {
    const resultado = liquidacaoSchema.safeParse({
      ...CAMPOS_VALIDOS,
      documentosSuporte: [{ tipo: '', numero: '', dataEmissao: '', referenciaExterna: '' }],
    });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      const mensagens = resultado.error.issues.map((i) => i.message);
      expect(mensagens).toEqual(
        expect.arrayContaining(['Informe o tipo do documento.', 'Informe o número do documento.', 'Informe a data de emissão.']),
      );
    }
  });

  it('referenciaExterna do documento de suporte é opcional', () => {
    const resultado = liquidacaoSchema.safeParse({
      ...CAMPOS_VALIDOS,
      documentosSuporte: [{ ...CAMPOS_VALIDOS.documentosSuporte[0], referenciaExterna: '' }],
    });
    expect(resultado.success).toBe(true);
  });
});
