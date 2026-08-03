import { describe, expect, it } from 'vitest';
import { aplicarFiltroEmSearchParams, filtroDeSearchParams, filtroParaApi, FILTRO_VAZIO, limparFiltroEmSearchParams } from './filtro';

describe('filtro (estado vive na URL — drill-down/UX-2)', () => {
  it('round-trip form -> searchParams -> form preserva os campos preenchidos', () => {
    const form = { ...FILTRO_VAZIO, estagio: 'pago', numeroEmpenho: '1024', credorId: 'credor-1' };
    const params = aplicarFiltroEmSearchParams(new URLSearchParams(), form);
    expect(filtroDeSearchParams(params)).toEqual(form);
  });

  it('preserva chaves fora do filtro (ex.: aba) ao aplicar/limpar', () => {
    const params = new URLSearchParams({ aba: 'totais' });
    const aplicado = aplicarFiltroEmSearchParams(params, { ...FILTRO_VAZIO, estagio: 'liquidado' });
    expect(aplicado.get('aba')).toBe('totais');
    expect(aplicado.get('estagio')).toBe('liquidado');

    const limpo = limparFiltroEmSearchParams(aplicado);
    expect(limpo.get('aba')).toBe('totais');
    expect(limpo.get('estagio')).toBeNull();
  });

  it('filtroParaApi omite campos vazios (não envia string vazia como parâmetro)', () => {
    expect(filtroParaApi(FILTRO_VAZIO)).toEqual({
      estagio: undefined,
      dataInicio: undefined,
      dataFim: undefined,
      numeroEmpenho: undefined,
      funcao: undefined,
      ordenarPor: 'publicadoEm',
      credorId: undefined,
      orgaoId: undefined,
      contratoId: undefined,
    });
  });
});
