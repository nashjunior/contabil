package br.contabil.execucao.application;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;
import java.time.Instant;
import java.util.Objects;

/** Sinal mínimo para medir o SLA legal da transparência (publicação <= 1o dia útil). */
public interface SinalizacaoSlaTransparenciaPort {

    void registrar(SinalPublicacao sinal);

    record SinalPublicacao(
            TenantId enteId,
            String tipoEvento,
            String recurso,
            IdEntrega entregaId,
            Instant registradoEm,
            Instant publicarAte,
            ResultadoSla resultado) {

        public SinalPublicacao {
            Objects.requireNonNull(enteId, "enteId");
            Objects.requireNonNull(tipoEvento, "tipoEvento");
            Objects.requireNonNull(recurso, "recurso");
            Objects.requireNonNull(entregaId, "entregaId");
            Objects.requireNonNull(registradoEm, "registradoEm");
            Objects.requireNonNull(publicarAte, "publicarAte");
            Objects.requireNonNull(resultado, "resultado");
        }
    }

    enum ResultadoSla {
        DENTRO_DO_PRAZO,
        ATRASADO
    }
}
