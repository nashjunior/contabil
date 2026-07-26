package br.contabil.consulta;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.contabil.assinatura.AssinaturaGovBrOAuthProvedorIndisponivelException;
import br.contabil.execucao.domain.AutoAprovacaoNaoPermitidaException;
import br.contabil.execucao.domain.EmpenhoAssinaturaConflitanteException;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.PagamentoNaoAprovadoException;
import br.contabil.execucao.domain.SaldoInsuficienteException;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.CertificadoInvalidoException;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelInsuficienteException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.NaoAutenticadoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.razao.domain.PeriodoEncerradoException;

/**
 * Formato de erro HTTP consistente para os {@code ErroContrato} de negócio já
 * existentes (RAZ-101 §3) — primeiro precedente do sistema: todo
 * {@code @RestController} futuro herda este mapeamento em vez de repetir
 * try/catch de infraestrutura em cada controller (guardiao-arquitetura —
 * controller não tem lógica própria).
 *
 * <p>{@link PeriodoEncerradoException} não implementa {@code ErroContrato}
 * (não tem {@code codigo()} próprio hoje) — o código {@code periodo_encerrado}
 * é fixado aqui; nenhum dos três use cases de consulta desta borda (RAZ-97)
 * toca {@code PeriodoContabilPort}, então este mapeamento é hoje só
 * forward-looking para os futuros endpoints de escrita.
 */
@RestControllerAdvice
class ErroContratoExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ErroContratoExceptionHandler.class);

    @ExceptionHandler(NaoAutenticadoException.class)
    ResponseEntity<ErroResponse> naoAutenticado(NaoAutenticadoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErroResponse(e.codigo()));
    }

    @ExceptionHandler(SemPermissaoException.class)
    ResponseEntity<ErroResponse> semPermissao(SemPermissaoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErroResponse(e.codigo()));
    }

    @ExceptionHandler(MfaRequeridoException.class)
    ResponseEntity<ErroResponse> mfaRequerido(MfaRequeridoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErroResponse(e.codigo()));
    }

    /**
     * RAZ-105/RAZ-79 §6.1: "conflito de saldo/estado" é {@code 409}, mais específico que o
     * {@code 400} genérico de {@link #execucaoInvalida} — cobre também {@link
     * AutoAprovacaoNaoPermitidaException} (viola a segregação de funções da Regra 9, um conflito de
     * estado da liquidação, não um payload malformado).
     */
    @ExceptionHandler({SaldoInsuficienteException.class, PagamentoNaoAprovadoException.class, AutoAprovacaoNaoPermitidaException.class})
    ResponseEntity<ErroResponse> conflitoDeSaldoOuEstado(ExecucaoInvalidaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResponse(e.codigo()));
    }

    @ExceptionHandler(ExecucaoInvalidaException.class)
    ResponseEntity<ErroResponse> execucaoInvalida(ExecucaoInvalidaException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(e.codigo()));
    }

    @ExceptionHandler(PeriodoEncerradoException.class)
    ResponseEntity<ErroResponse> periodoEncerrado(PeriodoEncerradoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResponse("periodo_encerrado"));
    }

    @ExceptionHandler(EmpenhoAssinaturaConflitanteException.class)
    ResponseEntity<ErroResponse> empenhoAssinaturaConflitante(EmpenhoAssinaturaConflitanteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResponse(e.codigo()));
    }

    @ExceptionHandler(CertificadoInvalidoException.class)
    ResponseEntity<ErroResponse> certificadoInvalido(CertificadoInvalidoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(e.codigo()));
    }

    @ExceptionHandler(NivelInsuficienteException.class)
    ResponseEntity<ErroResponse> nivelInsuficiente(NivelInsuficienteException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(e.codigo()));
    }

    @ExceptionHandler(AssinaturaGovBrOAuthProvedorIndisponivelException.class)
    ResponseEntity<ErroComCorrelacaoResponse> oauthProvedorIndisponivel(AssinaturaGovBrOAuthProvedorIndisponivelException e) {
        String correlationId = UUID.randomUUID().toString();
        LOG.error("Falha ao trocar code OAuth2 por token gov.br [correlationId={}]", correlationId, e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErroComCorrelacaoResponse(e.codigo(), correlationId));
    }

    record ErroResponse(String erro) {}

    record ErroComCorrelacaoResponse(String erro, String correlationId) {}
}
