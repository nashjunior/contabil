package br.contabil.consulta;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.execucao.application.ConsultarExecucaoOrcamentaria;
import br.contabil.execucao.application.ConsultarFilaAprovacao;
import br.contabil.execucao.application.ConsultarTrilhaLiquidacao;
import br.contabil.execucao.domain.FiltroFilaAprovacao;
import br.contabil.execucao.domain.ItemFilaAprovacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.PaginaFilaAprovacao;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.TrilhaLiquidacao;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Borda HTTP fina das consultas da execução (RAZ-101, RAZ-115/ADR-0029): só adapta
 * HTTP → {@code executar(Sessao, TenantId, ...)}, sem lógica de negócio (guardiao-arquitetura
 * — controller é {@code infra}/borda). A segregação da Regra 9 na fila e o recorte por
 * recurso da trilha vivem no servidor (use case + read model), nunca aqui. Erros de negócio
 * são mapeados por {@code ErroContratoExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/execucao")
final class ExecucaoConsultaController {

    private final ConsultarExecucaoOrcamentaria consultarExecucaoOrcamentaria;
    private final ConsultarFilaAprovacao consultarFilaAprovacao;
    private final ConsultarTrilhaLiquidacao consultarTrilhaLiquidacao;

    ExecucaoConsultaController(
            ConsultarExecucaoOrcamentaria consultarExecucaoOrcamentaria,
            ConsultarFilaAprovacao consultarFilaAprovacao,
            ConsultarTrilhaLiquidacao consultarTrilhaLiquidacao) {
        this.consultarExecucaoOrcamentaria =
                Objects.requireNonNull(consultarExecucaoOrcamentaria, "consultarExecucaoOrcamentaria");
        this.consultarFilaAprovacao = Objects.requireNonNull(consultarFilaAprovacao, "consultarFilaAprovacao");
        this.consultarTrilhaLiquidacao =
                Objects.requireNonNull(consultarTrilhaLiquidacao, "consultarTrilhaLiquidacao");
    }

    @GetMapping("/orcamentaria")
    ExecucaoOrcamentariaResponse orcamentaria(
            @PathVariable("enteId") UUID enteId,
            @RequestParam("exercicio") int exercicio,
            @RequestParam("mes") int mes,
            Sessao sessao) {
        var execucao = consultarExecucaoOrcamentaria.executar(sessao, new TenantId(enteId), exercicio, mes);
        return ExecucaoOrcamentariaResponse.de(execucao);
    }

    /** ADR-0029 §1: fila de aprovação por {@code statusAprovacao}, cursor opaco, PII do credor não exposta. */
    @GetMapping("/liquidacoes")
    FilaAprovacaoResponse fila(
            @PathVariable("enteId") UUID enteId,
            @RequestParam(value = "statusAprovacao", defaultValue = "pendente") String statusAprovacao,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "fonte", required = false) String fonte,
            @RequestParam(value = "dataInicio", required = false) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) LocalDate dataFim,
            @RequestParam(value = "valorMin", required = false) String valorMin,
            @RequestParam(value = "valorMax", required = false) String valorMax,
            Sessao sessao) {
        FiltroFilaAprovacao filtro = new FiltroFilaAprovacao(
                StatusAprovacao.valueOf(statusAprovacao.toUpperCase()),
                Optional.ofNullable(fonte),
                Optional.ofNullable(dataInicio),
                Optional.ofNullable(dataFim),
                Optional.ofNullable(valorMin).map(Dinheiro::de),
                Optional.ofNullable(valorMax).map(Dinheiro::de));
        PaginaFilaAprovacao pagina = consultarFilaAprovacao.executar(
                sessao, new TenantId(enteId), filtro, Optional.ofNullable(limit), Optional.ofNullable(cursor));
        return FilaAprovacaoResponse.de(pagina);
    }

    /** ADR-0029 §3: trilha dedicada da liquidação (empenho → liquidação → decisão), ator mascarado. */
    @GetMapping("/liquidacoes/{id}/trilha")
    TrilhaResponse trilha(@PathVariable("enteId") UUID enteId, @PathVariable("id") UUID id, Sessao sessao) {
        TrilhaLiquidacao trilha =
                consultarTrilhaLiquidacao.executar(sessao, new TenantId(enteId), new LiquidacaoId(id));
        return TrilhaResponse.de(trilha);
    }

    record FilaAprovacaoResponse(List<ItemFilaResponse> itens, String proximoCursor) {

        static FilaAprovacaoResponse de(PaginaFilaAprovacao pagina) {
            return new FilaAprovacaoResponse(
                    pagina.itens().stream().map(ItemFilaResponse::de).toList(),
                    pagina.proximoCursor().orElse(null));
        }
    }

    record ItemFilaResponse(
            UUID id,
            UUID empenhoId,
            long numeroEmpenho,
            int exercicioEmpenho,
            UUID credorId,
            String valor,
            LocalDate dataCompetencia,
            String statusAprovacao) {

        static ItemFilaResponse de(ItemFilaAprovacao item) {
            return new ItemFilaResponse(
                    item.id().valor(),
                    item.empenhoId().valor(),
                    item.numeroEmpenho(),
                    item.exercicioEmpenho(),
                    item.credorId().valor(),
                    item.valor().valor().toPlainString(),
                    item.dataCompetencia(),
                    item.statusAprovacao().name().toLowerCase());
        }
    }

    record TrilhaResponse(UUID liquidacaoId, List<EventoTrilhaResponse> eventos) {

        static TrilhaResponse de(TrilhaLiquidacao trilha) {
            return new TrilhaResponse(
                    trilha.liquidacaoId().valor(),
                    trilha.eventos().stream().map(EventoTrilhaResponse::de).toList());
        }
    }

    record EventoTrilhaResponse(String tipo, String ator, Instant quando, Map<String, String> detalhes) {

        static EventoTrilhaResponse de(EventoAuditoria evento) {
            return new EventoTrilhaResponse(evento.tipo(), evento.ator(), evento.momento(), evento.detalhes());
        }
    }
}
