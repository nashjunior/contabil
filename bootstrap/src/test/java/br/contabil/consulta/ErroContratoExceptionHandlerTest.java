package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.DesafioMfa;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.NaoAutenticadoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.razao.domain.PeriodoEncerradoException;

class ErroContratoExceptionHandlerTest {

    private final ErroContratoExceptionHandler handler = new ErroContratoExceptionHandler();

    @Test
    void naoAutenticadoDevolve401ComCodigoDoContrato() {
        var resposta = handler.naoAutenticado(new NaoAutenticadoException("credencial ausente"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody().erro()).isEqualTo("nao_autenticado");
    }

    @Test
    void semPermissaoDevolve403ComCodigoDoContrato() {
        var resposta = handler.semPermissao(new SemPermissaoException("ação negada"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().erro()).isEqualTo("sem_permissao");
    }

    @Test
    void mfaRequeridoDevolve403ComCodigoDoContrato() {
        var resposta = handler.mfaRequerido(new MfaRequeridoException(new DesafioMfa(UUID.randomUUID(), "canal")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().erro()).isEqualTo("mfa_requerido");
    }

    @Test
    void execucaoInvalidaDevolve400ComCodigoDoErro() {
        var resposta =
                handler.execucaoInvalida(new ExecucaoInvalidaException("periodo_invalido", "mes fora do intervalo"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().erro()).isEqualTo("periodo_invalido");
    }

    @Test
    void periodoEncerradoDevolve409ComCodigoFixo() {
        var resposta = handler.periodoEncerrado(
                new PeriodoEncerradoException(new TenantId(UUID.randomUUID()), LocalDate.of(2026, 1, 31)));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().erro()).isEqualTo("periodo_encerrado");
    }
}
