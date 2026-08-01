/**
 * Sem React/RHF, mesmo padrão de liquidacaoSchema.test.ts — cobre a regra central do
 * ADR-0055 decisão 3: motivo obrigatório só quando devolver.
 */
import { describe, expect, it } from 'vitest';
import { CAMPOS_DECISAO_APROVACAO_VAZIOS, decisaoAprovacaoSchema } from './decisaoAprovacaoSchema';

describe('decisaoAprovacaoSchema', () => {
  it('aprovar não exige motivo', () => {
    expect(decisaoAprovacaoSchema.safeParse({ decisao: 'aprovar', motivo: '' }).success).toBe(true);
  });

  it('devolver sem motivo é rejeitado com mensagem própria', () => {
    const resultado = decisaoAprovacaoSchema.safeParse({ decisao: 'devolver', motivo: '' });
    expect(resultado.success).toBe(false);
    if (!resultado.success) {
      expect(resultado.error.issues).toEqual([
        expect.objectContaining({ path: ['motivo'], message: 'Informe o motivo da devolução.' }),
      ]);
    }
  });

  it('devolver com motivo só de espaços em branco é rejeitado (mesma regra de isTextoObrigatorioValido)', () => {
    expect(decisaoAprovacaoSchema.safeParse({ decisao: 'devolver', motivo: '   ' }).success).toBe(false);
  });

  it('devolver com motivo preenchido é aceito', () => {
    expect(decisaoAprovacaoSchema.safeParse({ decisao: 'devolver', motivo: 'Documento fiscal divergente do empenho.' }).success).toBe(
      true,
    );
  });

  it('os valores vazios iniciais (decisao=aprovar) satisfazem o schema — modal abre já válido pro caminho padrão', () => {
    expect(decisaoAprovacaoSchema.safeParse(CAMPOS_DECISAO_APROVACAO_VAZIOS).success).toBe(true);
  });
});
