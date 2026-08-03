/**
 * Cobre o gap real verificado no produtor (`PublicadorTransparenciaExecucao`): o payload
 * varia por estágio — só `empenhado` carrega `numeroSequencial`/`credorId`/`unidadeGestoraId`.
 */
import { describe, expect, it } from 'vitest';
import {
  beneficiarioDoPayload,
  credorIdDoPayload,
  dataDoPayload,
  numeroEmpenhoDoPayload,
  orgaoIdDoPayload,
} from './payloadDespesa';

describe('payloadDespesa', () => {
  it('lê numeroSequencial/credorId/unidadeGestoraId/dataFato de um payload de empenho', () => {
    // `campo()` de `PublicadorTransparenciaExecucao` sempre serializa como STRING JSON
    // (inclusive `numeroSequencial`, via `Long.toString(...)` antes de aspar) — nunca número
    // cru. Confirmado lendo o produtor, não suposto.
    const payload = {
      estagio: 'empenhado',
      numeroSequencial: '1024',
      credorId: 'credor-1',
      unidadeGestoraId: 'orgao-1',
      dataFato: '2026-08-01',
    };
    expect(numeroEmpenhoDoPayload(payload)).toBe('1024');
    expect(credorIdDoPayload(payload)).toBe('credor-1');
    expect(orgaoIdDoPayload(payload)).toBe('orgao-1');
    expect(dataDoPayload(payload)).toBe('2026-08-01');
  });

  it('não fabrica credorId/orgaoId/numeroEmpenho para liquidação (campos ausentes no payload real)', () => {
    const payload = { estagio: 'liquidado', dataCompetencia: '2026-08-01', empenhoId: 'empenho-1' };
    expect(credorIdDoPayload(payload)).toBeNull();
    expect(orgaoIdDoPayload(payload)).toBeNull();
    expect(numeroEmpenhoDoPayload(payload)).toBeNull();
    expect(dataDoPayload(payload)).toBe('2026-08-01'); // fallback dataFato -> dataCompetencia
  });

  it('lê beneficiario apenas quando presente (pagamento a folha consolidada não tem beneficiário individual)', () => {
    expect(beneficiarioDoPayload({ beneficiario: { nome: 'João da Silva', documento: '***.456.***-**' } })).toEqual({
      nome: 'João da Silva',
      documento: '***.456.***-**',
    });
    expect(beneficiarioDoPayload({ beneficiario: null })).toBeNull();
    expect(beneficiarioDoPayload({})).toBeNull();
  });
});
