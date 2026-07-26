package br.contabil.plataforma.domain.auditoria;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;

/**
 * Detecção mínima de anomalia do piso F0 (docs/13-nfr-e-operacao.md §Piso de segurança F0;
 * RAZ-37): sinaliza acesso/operação <b>fora de alçada</b>, em <b>volume</b> anômalo na janela
 * recente, ou em <b>horário</b> incomum. Escala para PAM completo (JIT, dual control, replay
 * de sessão) só no F1 — aqui o corte é deliberadamente mínimo.
 *
 * <p>Regra pura (sem I/O, sem Spring — camada domain). Devolve os {@link EventoAuditoria}
 * das regras violadas; quem chama decide se ANEXA cada um via {@link AuditoriaEscrita#append}
 * (é isso que torna a anomalia rastreável na trilha hash-chain, ADR-0005) — o detector em si
 * não escreve na trilha.
 */
public final class DetectorAnomalia {

    /** Tipo do evento quando a alçada concedida ao ator é menor que a exigida pela ação. */
    public static final String ANOMALIA_FORA_ALCADA = "anomalia_fora_alcada";

    /** Tipo do evento quando o volume de acessos do ator na janela recente excede o limite. */
    public static final String ANOMALIA_VOLUME = "anomalia_volume_acesso";

    /** Tipo do evento quando o acesso ocorre fora do expediente esperado (horário/fim de semana). */
    public static final String ANOMALIA_HORARIO = "anomalia_horario_incomum";

    private final int limiteAcessosNaJanela;
    private final Duration janelaVolume;
    private final LocalTime inicioExpediente;
    private final LocalTime fimExpediente;
    private final ZoneId fusoExpediente;

    /** Limiares F0 default: 30 acessos / 5 min; expediente 07:00–20:00 America/Sao_Paulo, sem fim de semana. */
    public DetectorAnomalia() {
        this(30, Duration.ofMinutes(5), LocalTime.of(7, 0), LocalTime.of(20, 0), ZoneId.of("America/Sao_Paulo"));
    }

    public DetectorAnomalia(
            int limiteAcessosNaJanela,
            Duration janelaVolume,
            LocalTime inicioExpediente,
            LocalTime fimExpediente,
            ZoneId fusoExpediente) {
        if (limiteAcessosNaJanela <= 0) {
            throw new IllegalArgumentException("limite de acessos na janela deve ser positivo");
        }
        this.limiteAcessosNaJanela = limiteAcessosNaJanela;
        this.janelaVolume = Objects.requireNonNull(janelaVolume, "janela de volume");
        this.inicioExpediente = Objects.requireNonNull(inicioExpediente, "início do expediente");
        this.fimExpediente = Objects.requireNonNull(fimExpediente, "fim do expediente");
        this.fusoExpediente = Objects.requireNonNull(fusoExpediente, "fuso do expediente");
    }

    /**
     * Avalia um registro de acesso/operação contra as três regras mínimas do F0.
     *
     * @param registro acesso/operação sendo avaliado
     * @param acessosRecentesDoAtor momentos de acessos anteriores do MESMO ator (qualquer
     *     ordem; a janela é recortada internamente) — não inclui o próprio {@code registro}
     * @return um evento por regra violada (0 a 3); nunca nulo
     */
    public List<EventoAuditoria> avaliar(RegistroAcesso registro, List<Instant> acessosRecentesDoAtor) {
        Objects.requireNonNull(registro, "registro de acesso");
        Objects.requireNonNull(acessosRecentesDoAtor, "histórico de acessos recentes do ator");

        List<EventoAuditoria> anomalias = new ArrayList<>();

        if (registro.alcadaConcedida() < registro.alcadaExigida()) {
            anomalias.add(evento(
                    registro,
                    ANOMALIA_FORA_ALCADA,
                    Map.of(
                            "acao", registro.acao(),
                            "alcada_exigida", String.valueOf(registro.alcadaExigida()),
                            "alcada_concedida", String.valueOf(registro.alcadaConcedida()))));
        }

        long naJanela = acessosRecentesDoAtor.stream()
                        .filter(momento -> !momento.isBefore(registro.momento().minus(janelaVolume)))
                        .filter(momento -> !momento.isAfter(registro.momento()))
                        .count()
                + 1; // +1 pelo próprio registro avaliado
        if (naJanela > limiteAcessosNaJanela) {
            anomalias.add(evento(
                    registro,
                    ANOMALIA_VOLUME,
                    Map.of(
                            "acao", registro.acao(),
                            "contagem_janela", String.valueOf(naJanela),
                            "limite", String.valueOf(limiteAcessosNaJanela),
                            "janela_minutos", String.valueOf(janelaVolume.toMinutes()))));
        }

        ZonedDateTime local = registro.momento().atZone(fusoExpediente);
        boolean foraDoExpediente = local.getDayOfWeek() == DayOfWeek.SATURDAY
                || local.getDayOfWeek() == DayOfWeek.SUNDAY
                || local.toLocalTime().isBefore(inicioExpediente)
                || local.toLocalTime().isAfter(fimExpediente);
        if (foraDoExpediente) {
            anomalias.add(evento(
                    registro,
                    ANOMALIA_HORARIO,
                    Map.of(
                            "acao", registro.acao(),
                            "horario_local", local.toLocalTime().toString(),
                            "dia_semana", local.getDayOfWeek().toString())));
        }

        return List.copyOf(anomalias);
    }

    private static EventoAuditoria evento(RegistroAcesso registro, String tipo, Map<String, String> detalhes) {
        return new EventoAuditoria(
                registro.ente(), tipo, registro.ator(), registro.recurso(), registro.momento(), detalhes);
    }

    /**
     * Registro mínimo de um acesso/operação avaliado pelas regras de anomalia.
     * {@code alcadaExigida}/{@code alcadaConcedida} são níveis inteiros comparáveis definidos
     * pelo chamador (RBAC concreto é entregue à parte, RAZ-33/RAZ-5) — o detector só compara.
     */
    public record RegistroAcesso(
            TenantId ente,
            String ator,
            String recurso,
            String acao,
            Instant momento,
            int alcadaExigida,
            int alcadaConcedida) {
        public RegistroAcesso {
            Objects.requireNonNull(ente, "ente do acesso");
            Objects.requireNonNull(ator, "ator do acesso");
            Objects.requireNonNull(recurso, "recurso do acesso");
            Objects.requireNonNull(acao, "ação do acesso");
            Objects.requireNonNull(momento, "momento do acesso");
        }
    }
}
