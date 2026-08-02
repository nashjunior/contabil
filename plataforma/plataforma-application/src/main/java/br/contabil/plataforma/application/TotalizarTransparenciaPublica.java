package br.contabil.plataforma.application;

import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.transparencia.FiltroTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.TotalizacaoTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.TransparenciaPublicaQuery;

/** Totalização pública da transparência ativa, usando os mesmos filtros do detalhe. */
public final class TotalizarTransparenciaPublica {

    private final TransparenciaPublicaQuery query;

    public TotalizarTransparenciaPublica(TransparenciaPublicaQuery query) {
        this.query = Objects.requireNonNull(query, "query");
    }

    public TotalizacaoTransparenciaPublica executar(TenantId enteId, FiltroTransparenciaPublica filtro) {
        return query.totalizar(
                Objects.requireNonNull(enteId, "enteId"),
                Objects.requireNonNull(filtro, "filtro"));
    }
}
