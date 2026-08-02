package br.contabil.plataforma.application;

import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.transparencia.FiltroTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.PaginaTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.TransparenciaPublicaQuery;

/** Consulta pública de detalhe da transparência ativa. */
public final class ConsultarTransparenciaPublica {

    private final TransparenciaPublicaQuery query;

    public ConsultarTransparenciaPublica(TransparenciaPublicaQuery query) {
        this.query = Objects.requireNonNull(query, "query");
    }

    public PaginaTransparenciaPublica executar(TenantId enteId, FiltroTransparenciaPublica filtro) {
        return query.consultar(
                Objects.requireNonNull(enteId, "enteId"),
                Objects.requireNonNull(filtro, "filtro"));
    }
}
