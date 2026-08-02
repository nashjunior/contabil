package br.contabil.plataforma.domain.transparencia;

import br.contabil.plataforma.domain.TenantId;

/** Porta de leitura pública: somente read model de transparência, nunca OLTP/razão. */
public interface TransparenciaPublicaQuery {

    PaginaTransparenciaPublica consultar(TenantId enteId, FiltroTransparenciaPublica filtro);

    TotalizacaoTransparenciaPublica totalizar(TenantId enteId, FiltroTransparenciaPublica filtro);
}
