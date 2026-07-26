package br.contabil.escrita;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.execucao.application.RegistrarEmpenho;
import br.contabil.execucao.domain.ContratoId;
import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Borda HTTP do estágio de empenho (RAZ-105, RAZ-79 §6.3): só adapta HTTP →
 * {@code RegistrarEmpenho.executar(..)}, sem lógica de negócio
 * (guardiao-arquitetura — controller é infra/borda). Erros de negócio são
 * mapeados por {@code ErroContratoExceptionHandler} (RAZ-101).
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/execucao/empenhos")
final class EmpenhoController {

    private final RegistrarEmpenho registrarEmpenho;

    EmpenhoController(RegistrarEmpenho registrarEmpenho) {
        this.registrarEmpenho = Objects.requireNonNull(registrarEmpenho, "registrarEmpenho");
    }

    @PostMapping
    ResponseEntity<EmpenhoResponse> registrar(
            @PathVariable("enteId") UUID enteId, @RequestBody EmpenhoRequest requisicao, Sessao sessao) {
        Empenho empenho = registrarEmpenho.executar(
                sessao,
                new TenantId(enteId),
                new DotacaoId(requisicao.dotacaoId()),
                TipoEmpenho.deCodigo(requisicao.tipo()),
                new CredorId(requisicao.credorId()),
                new UnidadeGestoraId(requisicao.unidadeGestoraId()),
                requisicao.contratoId() == null ? null : new ContratoId(requisicao.contratoId()),
                Dinheiro.de(requisicao.valor()),
                requisicao.dataFato(),
                requisicao.exercicio(),
                requisicao.classificacaoOrcamentaria(),
                requisicao.fonteRecurso(),
                requisicao.historico());
        return ResponseEntity.status(HttpStatus.CREATED).body(EmpenhoResponse.de(empenho));
    }

    record EmpenhoRequest(
            UUID dotacaoId,
            String tipo,
            UUID credorId,
            UUID unidadeGestoraId,
            UUID contratoId,
            String valor,
            LocalDate dataFato,
            int exercicio,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            String historico) {}

    record EmpenhoResponse(
            UUID id,
            long numeroSequencial,
            int exercicio,
            String tipo,
            UUID dotacaoId,
            UUID credorId,
            UUID unidadeGestoraId,
            UUID contratoId,
            String valor,
            LocalDate dataFato,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            String historico,
            UUID fatoContabilId) {

        static EmpenhoResponse de(Empenho empenho) {
            return new EmpenhoResponse(
                    empenho.id().valor(),
                    empenho.numeroSequencial(),
                    empenho.exercicio(),
                    empenho.tipo().codigo(),
                    empenho.dotacaoId().valor(),
                    empenho.credorId().valor(),
                    empenho.unidadeGestoraId().valor(),
                    empenho.contratoId() == null ? null : empenho.contratoId().valor(),
                    empenho.valor().valor().toPlainString(),
                    empenho.dataFato(),
                    empenho.classificacaoOrcamentaria(),
                    empenho.fonteRecurso(),
                    empenho.historico(),
                    empenho.fatoContabilId().valor());
        }
    }
}
