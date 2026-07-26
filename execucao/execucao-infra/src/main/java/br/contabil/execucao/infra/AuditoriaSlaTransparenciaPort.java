package br.contabil.execucao.infra;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import br.contabil.execucao.application.SinalizacaoSlaTransparenciaPort;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;

/**
 * Sinal durável para apuração/alarme do SLA da transparência.
 *
 * <p>Não registra payload público nem dado pessoal; só metadados técnicos da entrega.
 */
@Component
public class AuditoriaSlaTransparenciaPort implements SinalizacaoSlaTransparenciaPort {

    private static final String ATOR_SISTEMA = "sistema:transparencia";

    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public AuditoriaSlaTransparenciaPort(AuditoriaEscrita auditoria, Clock clock) {
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "relógio");
    }

    @Override
    public void registrar(SinalPublicacao sinal) {
        auditoria.append(new EventoAuditoria(
                sinal.enteId(),
                "transparencia_execucao_publicacao_agendada",
                ATOR_SISTEMA,
                sinal.recurso(),
                Instant.now(clock),
                Map.of(
                        "tipoEvento", sinal.tipoEvento(),
                        "entregaId", sinal.entregaId().valor().toString(),
                        "registradoEm", sinal.registradoEm().toString(),
                        "publicarAte", sinal.publicarAte().toString(),
                        "resultadoSla", sinal.resultado().name())));
    }
}
