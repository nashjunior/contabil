package br.contabil.razao.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.TenantId;

class PeriodoContabilTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final Clock relogioFixo = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("encerra um período aberto comum (mês 1-12): transiciona status e grava encerradoEm")
    void encerraPeriodoAbertoComum() {
        PeriodoContabil periodo = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 7, StatusPeriodo.ABERTO, Optional.empty());

        PeriodoContabil encerrado = periodo.encerrar(relogioFixo);

        assertThat(encerrado.status()).isEqualTo(StatusPeriodo.ENCERRADO);
        assertThat(encerrado.encerradoEm()).contains(Instant.parse("2026-08-01T12:00:00Z"));
        assertThat(encerrado.id()).isEqualTo(periodo.id());
    }

    @Test
    @DisplayName("rejeita encerrar um período que já está encerrado")
    void rejeitaPeriodoJaEncerrado() {
        PeriodoContabil periodo = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(),
                enteId,
                2026,
                7,
                StatusPeriodo.ENCERRADO,
                Optional.of(Instant.parse("2026-07-31T23:59:59Z")));

        assertThatThrownBy(() -> periodo.encerrar(relogioFixo)).isInstanceOf(EncerramentoConflitanteException.class);
    }

    @Test
    @DisplayName("mês 13 (encerramento de exercício) exige EncerrarExercicio, não o encerramento simples")
    void rejeitaMes13SemApuracao() {
        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 13, StatusPeriodo.ABERTO, Optional.empty());

        assertThatThrownBy(() -> periodoExercicio.encerrar(relogioFixo))
                .isInstanceOf(PeriodoExercicioExigeApuracaoException.class);
    }
}
