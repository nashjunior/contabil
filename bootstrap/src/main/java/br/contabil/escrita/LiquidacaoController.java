package br.contabil.escrita;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.execucao.application.AprovarPagamento;
import br.contabil.execucao.application.RegistrarLiquidacao;
import br.contabil.execucao.domain.DecisaoAprovacao;
import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Borda HTTP do estágio de liquidação e do gate de aprovação (RAZ-105, RAZ-79
 * §6.4/§6.6): só adapta HTTP → {@code executar(..)}, sem lógica de negócio
 * (guardiao-arquitetura — controller é infra/borda). Erros de negócio são
 * mapeados por {@code ErroContratoExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/execucao/liquidacoes")
final class LiquidacaoController {

    private final RegistrarLiquidacao registrarLiquidacao;
    private final AprovarPagamento aprovarPagamento;

    LiquidacaoController(RegistrarLiquidacao registrarLiquidacao, AprovarPagamento aprovarPagamento) {
        this.registrarLiquidacao = Objects.requireNonNull(registrarLiquidacao, "registrarLiquidacao");
        this.aprovarPagamento = Objects.requireNonNull(aprovarPagamento, "aprovarPagamento");
    }

    @PostMapping
    ResponseEntity<LiquidacaoResponse> registrar(
            @PathVariable("enteId") UUID enteId, @RequestBody LiquidacaoRequest requisicao, Sessao sessao) {
        Liquidacao liquidacao = registrarLiquidacao.executar(
                sessao,
                new TenantId(enteId),
                new EmpenhoId(requisicao.empenhoId()),
                requisicao.dataCompetencia(),
                Dinheiro.de(requisicao.valor()),
                requisicao.documentosSuporte().stream().map(DocumentoSuporteRequest::paraDominio).toList(),
                requisicao.historico());
        return ResponseEntity.status(HttpStatus.CREATED).body(LiquidacaoResponse.de(liquidacao));
    }

    @PostMapping("/{id}/aprovacao")
    LiquidacaoResponse aprovar(
            @PathVariable("enteId") UUID enteId,
            @PathVariable("id") UUID id,
            @RequestBody AprovacaoRequest requisicao,
            Sessao sessao) {
        Liquidacao decidida = aprovarPagamento.executar(
                sessao,
                new TenantId(enteId),
                new LiquidacaoId(id),
                DecisaoAprovacao.valueOf(requisicao.decisao().toUpperCase()),
                Optional.ofNullable(requisicao.motivo()));
        return LiquidacaoResponse.de(decidida);
    }

    record DocumentoSuporteRequest(String tipo, String numero, LocalDate dataEmissao, String referenciaExterna) {

        DocumentoSuporte paraDominio() {
            return new DocumentoSuporte(tipo, numero, dataEmissao, Optional.ofNullable(referenciaExterna));
        }
    }

    record LiquidacaoRequest(
            UUID empenhoId,
            LocalDate dataCompetencia,
            String valor,
            List<DocumentoSuporteRequest> documentosSuporte,
            String historico) {}

    record AprovacaoRequest(String decisao, String motivo) {}

    record LiquidacaoResponse(
            UUID id,
            UUID empenhoId,
            LocalDate dataCompetencia,
            String valor,
            List<DocumentoSuporteResponse> documentosSuporte,
            String historico,
            UUID fatoContabilId,
            String status) {

        /**
         * {@code status} espelha {@code statusAprovacao} do agregado (pendente|aprovada|devolvida)
         * — os rótulos {@code registrada}/{@code paga_parcial}/{@code paga_total} do contrato
         * (RAZ-79 §6.4) são leitura derivada do saldo (fora do escopo desta borda de escrita; ver
         * consulta de saldo, §6.2/RAZ-97).
         */
        static LiquidacaoResponse de(Liquidacao liquidacao) {
            return new LiquidacaoResponse(
                    liquidacao.id().valor(),
                    liquidacao.empenhoId().valor(),
                    liquidacao.dataCompetencia(),
                    liquidacao.valor().valor().toPlainString(),
                    liquidacao.documentosSuporte().stream().map(DocumentoSuporteResponse::de).toList(),
                    liquidacao.historico(),
                    liquidacao.fatoContabilId().valor(),
                    liquidacao.statusAprovacao().name().toLowerCase());
        }
    }

    record DocumentoSuporteResponse(String tipo, String numero, LocalDate dataEmissao, String referenciaExterna) {

        static DocumentoSuporteResponse de(DocumentoSuporte documento) {
            return new DocumentoSuporteResponse(
                    documento.tipo(),
                    documento.numero(),
                    documento.dataEmissao(),
                    documento.referenciaExterna().orElse(null));
        }
    }
}
