package br.contabil.plataforma.infra.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import br.contabil.plataforma.domain.cofre.CofreSegredos;
import br.contabil.plataforma.domain.cofre.CofreSegredos.ContaServico;
import br.contabil.plataforma.domain.cofre.CofreSegredos.ReferenciaSegredo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CofreSegredosVariaveisAmbienteTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void resolveReferenciaLogicaSemExporValorEmToString() {
        var cofre = new CofreSegredosVariaveisAmbiente(
                Map.of("SIAFIC_F0_DB_RUNTIME_PASSWORD", "senha-do-cofre")::get,
                CLOCK,
                Duration.ofMinutes(5));

        var valor = cofre.obter(
                new ContaServico("runtime", Set.of("cofre://siafic/f0/db")),
                new ReferenciaSegredo("cofre://siafic/f0/db/runtime/password"));

        assertThat(valor.valor()).containsExactly("senha-do-cofre".toCharArray());
        assertThat(valor.expiraEm()).isEqualTo(Instant.parse("2026-07-19T12:05:00Z"));
        assertThat(valor.toString()).doesNotContain("senha-do-cofre").contains("REDIGIDO");
    }

    @Test
    void recusaContaSemEscopoParaReferencia() {
        var cofre = new CofreSegredosVariaveisAmbiente(
                Map.of("SIAFIC_F0_DB_RUNTIME_PASSWORD", "senha-do-cofre")::get,
                CLOCK,
                Duration.ofMinutes(5));

        assertThatExceptionOfType(CofreSegredos.SemEscopoException.class)
                .isThrownBy(() -> cofre.obter(
                        new ContaServico("runtime", Set.of("cofre://siafic/f0/assinatura")),
                        new ReferenciaSegredo("cofre://siafic/f0/db/runtime/password")));
    }

    @Test
    void permiteReferenciaEnvExplicitaParaCofreLocal() {
        assertThat(CofreSegredosVariaveisAmbiente.nomeVariavel(new ReferenciaSegredo("env:DB_RUNTIME_PASSWORD")))
                .isEqualTo("DB_RUNTIME_PASSWORD");
    }
}
