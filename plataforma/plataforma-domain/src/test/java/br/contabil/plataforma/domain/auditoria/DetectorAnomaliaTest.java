package br.contabil.plataforma.domain.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.DetectorAnomalia.RegistroAcesso;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova as três regras mínimas de anomalia do piso F0 (13-nfr §piso; RAZ-37): fora de alçada,
 * volume e horário. Cada evento devolvido é o que a aplicação anexa via
 * {@link AuditoriaEscrita#append} para ficar rastreável na trilha hash-chain.
 */
class DetectorAnomaliaTest {

    private static final TenantId ENTE = new TenantId(UUID.randomUUID());
    // Terça-feira, 10:00 America/Sao_Paulo — dentro do expediente default.
    private static final Instant HORARIO_COMERCIAL = Instant.parse("2026-07-21T13:00:00Z");
    // Domingo — fora do expediente default em qualquer horário.
    private static final Instant FIM_DE_SEMANA = Instant.parse("2026-07-19T15:00:00Z");

    private final DetectorAnomalia detector = new DetectorAnomalia();

    @Test
    @DisplayName("acesso dentro de alçada, volume baixo e horário comercial não gera anomalia")
    void acessoNormalNaoGeraAnomalia() {
        RegistroAcesso registro = registro(HORARIO_COMERCIAL, 2, 2);

        List<EventoAuditoria> anomalias = detector.avaliar(registro, List.of());

        assertThat(anomalias).isEmpty();
    }

    @Test
    @DisplayName("alçada concedida menor que a exigida gera anomalia_fora_alcada rastreável")
    void foraDeAlcadaGeraAnomalia() {
        RegistroAcesso registro = registro(HORARIO_COMERCIAL, 3, 1);

        List<EventoAuditoria> anomalias = detector.avaliar(registro, List.of());

        assertThat(anomalias).hasSize(1);
        EventoAuditoria evento = anomalias.get(0);
        assertThat(evento.tipo()).isEqualTo(DetectorAnomalia.ANOMALIA_FORA_ALCADA);
        assertThat(evento.ente()).isEqualTo(ENTE);
        assertThat(evento.ator()).isEqualTo("operador-teste-1");
        assertThat(evento.recurso()).isEqualTo("razao:fato_contabil");
        assertThat(evento.detalhes())
                .containsEntry("alcada_exigida", "3")
                .containsEntry("alcada_concedida", "1");
    }

    @Test
    @DisplayName("alçada concedida igual ou maior que a exigida não é anomalia")
    void alcadaSuficienteNaoGeraAnomalia() {
        assertThat(detector.avaliar(registro(HORARIO_COMERCIAL, 2, 2), List.of())).isEmpty();
        assertThat(detector.avaliar(registro(HORARIO_COMERCIAL, 2, 5), List.of())).isEmpty();
    }

    @Test
    @DisplayName("volume de acessos acima do limite na janela recente gera anomalia_volume_acesso")
    void volumeAcimaDoLimiteGeraAnomalia() {
        DetectorAnomalia detectorComLimiteBaixo =
                new DetectorAnomalia(3, Duration.ofMinutes(5), LocalTime.of(7, 0), LocalTime.of(20, 0), ZoneId.of(
                        "America/Sao_Paulo"));
        List<Instant> acessosRecentes = List.of(
                HORARIO_COMERCIAL.minusSeconds(60),
                HORARIO_COMERCIAL.minusSeconds(120),
                HORARIO_COMERCIAL.minusSeconds(180));

        List<EventoAuditoria> anomalias =
                detectorComLimiteBaixo.avaliar(registro(HORARIO_COMERCIAL, 1, 1), acessosRecentes);

        assertThat(anomalias).hasSize(1);
        assertThat(anomalias.get(0).tipo()).isEqualTo(DetectorAnomalia.ANOMALIA_VOLUME);
        assertThat(anomalias.get(0).detalhes()).containsEntry("contagem_janela", "4").containsEntry("limite", "3");
    }

    @Test
    @DisplayName("acesso antigo fora da janela de volume não conta para o limite")
    void acessoForaDaJanelaNaoContaParaVolume() {
        DetectorAnomalia detectorComLimiteBaixo =
                new DetectorAnomalia(1, Duration.ofMinutes(5), LocalTime.of(7, 0), LocalTime.of(20, 0), ZoneId.of(
                        "America/Sao_Paulo"));
        List<Instant> acessoAntigo = List.of(HORARIO_COMERCIAL.minus(Duration.ofHours(1)));

        List<EventoAuditoria> anomalias =
                detectorComLimiteBaixo.avaliar(registro(HORARIO_COMERCIAL, 1, 1), acessoAntigo);

        assertThat(anomalias).isEmpty();
    }

    @Test
    @DisplayName("acesso em fim de semana gera anomalia_horario_incomum")
    void acessoNoFimDeSemanaGeraAnomalia() {
        List<EventoAuditoria> anomalias = detector.avaliar(registro(FIM_DE_SEMANA, 1, 1), List.of());

        assertThat(anomalias).hasSize(1);
        assertThat(anomalias.get(0).tipo()).isEqualTo(DetectorAnomalia.ANOMALIA_HORARIO);
        assertThat(anomalias.get(0).detalhes()).containsEntry("dia_semana", "SUNDAY");
    }

    @Test
    @DisplayName("acesso de madrugada em dia útil gera anomalia_horario_incomum")
    void acessoDeMadrugadaGeraAnomalia() {
        // Terça-feira, 03:00 America/Sao_Paulo.
        Instant madrugada = Instant.parse("2026-07-21T06:00:00Z");

        List<EventoAuditoria> anomalias = detector.avaliar(registro(madrugada, 1, 1), List.of());

        assertThat(anomalias).hasSize(1);
        assertThat(anomalias.get(0).tipo()).isEqualTo(DetectorAnomalia.ANOMALIA_HORARIO);
    }

    @Test
    @DisplayName("mais de uma regra pode disparar no mesmo acesso — todas viram evento")
    void multiplasRegrasPodemDispararJuntas() {
        List<EventoAuditoria> anomalias = detector.avaliar(registro(FIM_DE_SEMANA, 5, 1), List.of());

        assertThat(anomalias)
                .extracting(EventoAuditoria::tipo)
                .containsExactlyInAnyOrder(DetectorAnomalia.ANOMALIA_FORA_ALCADA, DetectorAnomalia.ANOMALIA_HORARIO);
    }

    @Test
    @DisplayName("limite de acessos na janela deve ser positivo")
    void limiteInvalidoRejeitado() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DetectorAnomalia(
                        0, Duration.ofMinutes(5), LocalTime.of(7, 0), LocalTime.of(20, 0), ZoneId.of(
                                "America/Sao_Paulo")));
    }

    private static RegistroAcesso registro(Instant momento, int alcadaExigida, int alcadaConcedida) {
        // Ator identificado por rótulo sintético, não CPF (13-nfr §piso "sem PII em não-produção").
        return new RegistroAcesso(
                ENTE, "operador-teste-1", "razao:fato_contabil", "ESTORNAR", momento, alcadaExigida, alcadaConcedida);
    }
}
