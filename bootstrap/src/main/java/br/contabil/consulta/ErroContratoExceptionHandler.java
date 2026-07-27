package br.contabil.consulta;

import java.util.Map;
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
import br.contabil.execucao.domain.LiquidacaoJaDecididaException;
import br.contabil.execucao.domain.PagamentoNaoAprovadoException;
import br.contabil.execucao.domain.SaldoInsuficienteException;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.CertificadoInvalidoException;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelInsuficienteException;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoNaoEncontradoException;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoTenantInvalidoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.NaoAutenticadoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.razao.domain.ContaNaoEncontradaException;
import br.contabil.razao.domain.PeriodoEncerradoException;

/**
 * Formato de erro HTTP consistente para os {@code ErroContrato} de negócio já
 * existentes (RAZ-101 §3) — primeiro precedente do sistema: todo
 * {@code @RestController} futuro herda este mapeamento em vez de repetir
 * try/catch de infraestrutura em cada controller (guardiao-arquitetura —
 * controller não tem lógica própria).
 *
 * <p>O corpo é o <b>envelope único</b> {@code {codigo, mensagem, detalhes}}
 * (RAZ-79 §6.1 / ADR-0030 §3): {@code codigo} é o contrato estável legível por
 * máquina ({@code ErroContrato.codigo()}); {@code mensagem} é o texto humano da
 * exceção; {@code detalhes} carrega pares extras (ex.: {@code correlationId}).
 * Não existe segunda taxonomia de erro na borda HTTP — o §6.1 a proíbe.
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
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(corpo(e.codigo(), e));
    }

    @ExceptionHandler(SemPermissaoException.class)
    ResponseEntity<ErroResponse> semPermissao(SemPermissaoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(corpo(e.codigo(), e));
    }

    /**
     * RAZ-79 §6.1: {@code mfa_requerido} é {@code 428} (<i>Precondition Required</i>) — a
     * requisição precisa do segundo fator antes de prosseguir, não é uma negação de
     * autorização ({@code 403}). O cliente eleva a sessão (MFA) e repete a chamada.
     */
    @ExceptionHandler(MfaRequeridoException.class)
    ResponseEntity<ErroResponse> mfaRequerido(MfaRequeridoException e) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(corpo(e.codigo(), e));
    }

    /**
     * RAZ-105/RAZ-79 §6.1: "conflito de saldo/estado" é {@code 409}, mais específico que o
     * {@code 400} genérico de {@link #execucaoInvalida} — cobre também {@link
     * AutoAprovacaoNaoPermitidaException} (viola a segregação de funções da Regra 9, um conflito de
     * estado da liquidação, não um payload malformado) e {@link LiquidacaoJaDecididaException}
     * (dupla decisão sobre a mesma liquidação — ADR-0029 §4, mapeado por tipo, não por string).
     */
    @ExceptionHandler({
        SaldoInsuficienteException.class,
        PagamentoNaoAprovadoException.class,
        AutoAprovacaoNaoPermitidaException.class,
        LiquidacaoJaDecididaException.class
    })
    ResponseEntity<ErroResponse> conflitoDeSaldoOuEstado(ExecucaoInvalidaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo(e.codigo(), e));
    }

    @ExceptionHandler(ExecucaoInvalidaException.class)
    ResponseEntity<ErroResponse> execucaoInvalida(ExecucaoInvalidaException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(e.codigo(), e));
    }

    /**
     * RAZ-117/ADR-0030 §6 (gap 2): conta consultada não existe em {@code conta_pcasp} para o ente —
     * {@code 404}, distinto de "conta existente sem lançamento" (saldo zero, {@code 200}). Usa o
     * envelope único {@code {codigo, mensagem, detalhes}} convergido pela RAZ-116, sem taxonomia nova.
     */
    @ExceptionHandler(ContaNaoEncontradaException.class)
    ResponseEntity<ErroResponse> contaNaoEncontrada(ContaNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(e.codigo(), e));
    }

    @ExceptionHandler(PeriodoEncerradoException.class)
    ResponseEntity<ErroResponse> periodoEncerrado(PeriodoEncerradoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo("periodo_encerrado", e));
    }

    /**
     * RAZ-157/ADR-0009: empenho sem documento (nem pendente nem assinado) ou id
     * inexistente vira {@code documento_nao_encontrado}, {@code 404} — mesmo
     * envelope único, nenhuma taxonomia nova.
     */
    @ExceptionHandler(DocumentoNaoEncontradoException.class)
    ResponseEntity<ErroResponse> documentoNaoEncontrado(DocumentoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(e.codigo(), e));
    }

    /**
     * RAZ-157/ADR-0009 (RAZ-45): defesa em profundidade — URI cross-tenant nunca
     * chega a resolver conteúdo; devolve o mesmo {@code 404} de "não encontrado"
     * em vez de confirmar ao cliente que a URI existe, só que de outro ente.
     */
    @ExceptionHandler(DocumentoTenantInvalidoException.class)
    ResponseEntity<ErroResponse> documentoTenantInvalido(DocumentoTenantInvalidoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(e.codigo(), e));
    }

    @ExceptionHandler(EmpenhoAssinaturaConflitanteException.class)
    ResponseEntity<ErroResponse> empenhoAssinaturaConflitante(EmpenhoAssinaturaConflitanteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo(e.codigo(), e));
    }

    @ExceptionHandler(CertificadoInvalidoException.class)
    ResponseEntity<ErroResponse> certificadoInvalido(CertificadoInvalidoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(e.codigo(), e));
    }

    @ExceptionHandler(NivelInsuficienteException.class)
    ResponseEntity<ErroResponse> nivelInsuficiente(NivelInsuficienteException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo(e.codigo(), e));
    }

    @ExceptionHandler(AssinaturaGovBrOAuthProvedorIndisponivelException.class)
    ResponseEntity<ErroResponse> oauthProvedorIndisponivel(AssinaturaGovBrOAuthProvedorIndisponivelException e) {
        String correlationId = UUID.randomUUID().toString();
        LOG.error("Falha ao trocar code OAuth2 por token gov.br [correlationId={}]", correlationId, e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErroResponse(e.codigo(), mensagemDe(e, e.codigo()), Map.of("correlationId", correlationId)));
    }

    /** Monta o envelope {@code {codigo, mensagem, detalhes}} sem detalhes extras. */
    private static ErroResponse corpo(String codigo, Throwable e) {
        return new ErroResponse(codigo, mensagemDe(e, codigo), Map.of());
    }

    /** Texto humano da exceção; cai para o próprio código quando a exceção não traz mensagem. */
    private static String mensagemDe(Throwable e, String codigo) {
        return e.getMessage() != null ? e.getMessage() : codigo;
    }

    /**
     * Envelope de erro único da borda HTTP (RAZ-79 §6.1). {@code detalhes} é sempre
     * presente (vazio quando não há pares extras), para o cliente não ramificar por
     * ausência de campo.
     */
    record ErroResponse(String codigo, String mensagem, Map<String, String> detalhes) {}
}
