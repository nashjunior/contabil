package br.contabil.plataforma.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.cofre.CofreSegredos;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.ingestao.ServicoIngestao;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testa o <b>contrato</b> dos serviços de plataforma (doc 11 §Contratos), não as
 * implementações (entregues em RAZ-5/6/9/11/12). Trava a taxonomia estável de erros e as
 * invariantes de segurança dos value objects — mudá-las é breaking change (versionar em ADR).
 */
class ContratosPlataformaTest {

    @Test
    @DisplayName("taxonomia de erros é estável e legível por máquina (doc 11 §Contratos)")
    void taxonomiaDeErros() {
        assertThat(new ServicoIdentidade.NaoAutenticadoException("x").codigo()).isEqualTo("nao_autenticado");
        assertThat(new ServicoIdentidade.SemPermissaoException("x").codigo()).isEqualTo("sem_permissao");
        assertThat(new ServicoIdentidade.MfaRequeridoException(
                        new ServicoIdentidade.DesafioMfa(java.util.UUID.randomUUID(), "sms"))
                .codigo())
                .isEqualTo("mfa_requerido");
        assertThat(new ServicoAssinatura.CertificadoInvalidoException("x").codigo()).isEqualTo("certificado_invalido");
        assertThat(new ServicoAssinatura.NivelInsuficienteException("x").codigo()).isEqualTo("nivel_insuficiente");
        assertThat(new AuditoriaEscrita.FalhaAppendException("x", null).codigo()).isEqualTo("append_falhou");
        assertThat(new ServicoMascaramento.SemBaseLegalException("x").codigo()).isEqualTo("sem_base_legal");
        assertThat(new ServicoIngestao.OrigemNaoConfiavelException("x").codigo()).isEqualTo("origem_nao_confiavel");
        assertThat(new ServicoIngestao.AssinaturaInvalidaException("x").codigo()).isEqualTo("assinatura_invalida");
        assertThat(new CofreSegredos.SemEscopoException("x").codigo()).isEqualTo("sem_escopo");
        assertThat(new CofreSegredos.SegredoExpiradoException("x").codigo()).isEqualTo("expirado");
    }

    @Test
    @DisplayName("todo erro de contrato expõe ErroContrato#codigo")
    void errosImplementamContrato() {
        ErroContrato erro = new CofreSegredos.SemEscopoException("x");
        assertThat(erro).isInstanceOf(RuntimeException.class);
        assertThat(erro.codigo()).isNotBlank();
    }

    @Test
    @DisplayName("chave de idempotência rejeita valor vazio/nulo")
    void chaveIdempotencia() {
        assertThatIllegalArgumentException().isThrownBy(() -> ChaveIdempotencia.de("  "));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> ChaveIdempotencia.de(null));
        assertThat(ChaveIdempotencia.de("k-1").valor()).isEqualTo("k-1");
    }

    @Test
    @DisplayName("ValorSegredo nunca expõe o segredo em toString e devolve cópia defensiva")
    void segredoNaoVaza() {
        char[] segredo = "super-secreta".toCharArray();
        CofreSegredos.ValorSegredo valor =
                new CofreSegredos.ValorSegredo(segredo, Instant.parse("2030-01-01T00:00:00Z"));

        // toString redige — não contém o segredo.
        assertThat(valor.toString()).doesNotContain("super-secreta").contains("REDIGIDO");

        // cópia defensiva na entrada: mutar o array original não afeta o guardado.
        Arrays.fill(segredo, 'x');
        assertThat(valor.valor()).containsExactly("super-secreta".toCharArray());

        // cópia defensiva na saída: mutar o retornado não afeta o guardado.
        char[] devolvido = valor.valor();
        Arrays.fill(devolvido, 'z');
        assertThat(valor.valor()).containsExactly("super-secreta".toCharArray());
    }
}
