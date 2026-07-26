package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import br.contabil.assinatura.AssinaturaGovBrOAuthProvedorIndisponivelException;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.LiquidacaoJaDecididaException;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.DesafioMfa;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.NaoAutenticadoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.razao.domain.PeriodoEncerradoException;

class ErroContratoExceptionHandlerTest {

    private final ErroContratoExceptionHandler handler = new ErroContratoExceptionHandler();

    @Test
    void naoAutenticadoDevolve401ComEnvelopeCodigoMensagemDetalhes() {
        var resposta = handler.naoAutenticado(new NaoAutenticadoException("credencial ausente"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody().codigo()).isEqualTo("nao_autenticado");
        assertThat(resposta.getBody().mensagem()).isEqualTo("credencial ausente");
        assertThat(resposta.getBody().detalhes()).isEmpty();
    }

    @Test
    void semPermissaoDevolve403ComCodigoDoContrato() {
        var resposta = handler.semPermissao(new SemPermissaoException("ação negada"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().codigo()).isEqualTo("sem_permissao");
        assertThat(resposta.getBody().mensagem()).isEqualTo("ação negada");
    }

    @Test
    void mfaRequeridoDevolve428ComCodigoDoContrato() {
        var resposta = handler.mfaRequerido(new MfaRequeridoException(new DesafioMfa(UUID.randomUUID(), "canal")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(resposta.getBody().codigo()).isEqualTo("mfa_requerido");
    }

    @Test
    void execucaoInvalidaDevolve400ComCodigoDoErro() {
        var resposta =
                handler.execucaoInvalida(new ExecucaoInvalidaException("periodo_invalido", "mes fora do intervalo"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("periodo_invalido");
        assertThat(resposta.getBody().mensagem()).isEqualTo("mes fora do intervalo");
    }

    @Test
    void liquidacaoJaDecididaDevolve409ComCodigoDoContrato() {
        var resposta = handler.conflitoDeSaldoOuEstado(
                new LiquidacaoJaDecididaException(LiquidacaoId.novo(), StatusAprovacao.APROVADA));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().codigo()).isEqualTo("liquidacao_ja_decidida");
    }

    @Test
    void periodoEncerradoDevolve409ComCodigoFixo() {
        var resposta = handler.periodoEncerrado(
                new PeriodoEncerradoException(new TenantId(UUID.randomUUID()), LocalDate.of(2026, 1, 31)));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().codigo()).isEqualTo("periodo_encerrado");
    }

    @Test
    void oauthProvedorIndisponivelDevolve502ComCorrelationIdEmDetalhes() {
        var resposta = handler.oauthProvedorIndisponivel(
                new AssinaturaGovBrOAuthProvedorIndisponivelException(new IllegalStateException("token endpoint 503")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resposta.getBody().codigo()).isEqualTo("oauth_provedor_indisponivel");
        assertThat(resposta.getBody().detalhes().get("correlationId")).isNotBlank();
    }
}
