package br.contabil.consulta;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.contabil.execucao.domain.ExecucaoInvalidaException;
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

    @ExceptionHandler(ExecucaoInvalidaException.class)
    ResponseEntity<ErroResponse> execucaoInvalida(ExecucaoInvalidaException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(e.codigo()));
    }

    @ExceptionHandler(PeriodoEncerradoException.class)
    ResponseEntity<ErroResponse> periodoEncerrado(PeriodoEncerradoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResponse("periodo_encerrado"));
    }

    record ErroResponse(String erro) {}
}
