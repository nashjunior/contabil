package br.contabil.sessao;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

class SessaoAtualControllerTest {

    private final SessaoAtualController controller = new SessaoAtualController();

    @Test
    void atualDevolveCpfMascaradoEnteOrgaoEMfaDaSessaoAutenticadaSemNuncaVazarOCpfCru() {
        UUID enteId = UUID.randomUUID();
        Sessao sessao = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                new TenantId(enteId),
                Optional.of("Secretaria de Fazenda"),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));

        var resposta = controller.atual(sessao);

        assertThat(resposta.cpfMascarado()).isEqualTo("***.456.***-**").doesNotContain("12345678901");
        assertThat(resposta.enteId()).isEqualTo(enteId);
        assertThat(resposta.orgao()).isEqualTo("Secretaria de Fazenda");
        assertThat(resposta.mfaConcluido()).isTrue();
        assertThat(resposta.toString()).doesNotContain("12345678901");
    }

    @Test
    void atualComOrgaoAusenteDevolveOrgaoNulo() {
        Sessao sessao = new Sessao(
                UUID.randomUUID(),
                new Cpf("98765432109"),
                new TenantId(UUID.randomUUID()),
                Optional.empty(),
                false,
                Instant.parse("2030-01-01T00:00:00Z"));

        var resposta = controller.atual(sessao);

        assertThat(resposta.orgao()).isNull();
        assertThat(resposta.mfaConcluido()).isFalse();
    }
}
