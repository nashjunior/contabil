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

import br.contabil.execucao.application.ConsultarEmpenhosRegistrados;
import br.contabil.execucao.application.ConsultarExecucaoOrcamentaria;
import br.contabil.execucao.application.ConsultarFilaAprovacao;
import br.contabil.execucao.application.ConsultarLiquidacoesRegistradas;
import br.contabil.execucao.application.ConsultarPagamentosRegistrados;
import br.contabil.execucao.application.ConsultarTrilhaLiquidacao;
import br.contabil.execucao.domain.FiltroFilaAprovacao;
import br.contabil.execucao.domain.ItemEmpenhoRegistrado;
import br.contabil.execucao.domain.ItemFilaAprovacao;
import br.contabil.execucao.domain.ItemLiquidacaoRegistrada;
import br.contabil.execucao.domain.ItemPagamentoRegistrado;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.PaginaEmpenhosRegistrados;
import br.contabil.execucao.domain.PaginaFilaAprovacao;
import br.contabil.execucao.domain.PaginaLiquidacoesRegistradas;
import br.contabil.execucao.domain.PaginaPagamentosRegistrados;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.TrilhaLiquidacao;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Borda HTTP fina das consultas da execução (RAZ-101, RAZ-115/ADR-0029, RAZ-121): só adapta
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
    private final ConsultarEmpenhosRegistrados consultarEmpenhosRegistrados;
    private final ConsultarLiquidacoesRegistradas consultarLiquidacoesRegistradas;
    private final ConsultarPagamentosRegistrados consultarPagamentosRegistrados;

    ExecucaoConsultaController(
            ConsultarExecucaoOrcamentaria consultarExecucaoOrcamentaria,
            ConsultarFilaAprovacao consultarFilaAprovacao,
            ConsultarTrilhaLiquidacao consultarTrilhaLiquidacao,
            ConsultarEmpenhosRegistrados consultarEmpenhosRegistrados,
            ConsultarLiquidacoesRegistradas consultarLiquidacoesRegistradas,
            ConsultarPagamentosRegistrados consultarPagamentosRegistrados) {
        this.consultarExecucaoOrcamentaria =
                Objects.requireNonNull(consultarExecucaoOrcamentaria, "consultarExecucaoOrcamentaria");
        this.consultarFilaAprovacao = Objects.requireNonNull(consultarFilaAprovacao, "consultarFilaAprovacao");
        this.consultarTrilhaLiquidacao =
                Objects.requireNonNull(consultarTrilhaLiquidacao, "consultarTrilhaLiquidacao");
        this.consultarEmpenhosRegistrados =
                Objects.requireNonNull(consultarEmpenhosRegistrados, "consultarEmpenhosRegistrados");
        this.consultarLiquidacoesRegistradas =
                Objects.requireNonNull(consultarLiquidacoesRegistradas, "consultarLiquidacoesRegistradas");
        this.consultarPagamentosRegistrados =
                Objects.requireNonNull(consultarPagamentosRegistrados, "consultarPagamentosRegistrados");
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

    /**
     * RAZ-121: registro completo de empenhos por ente/período — a listagem persistida que
     * faltava para a tela de "execuções registradas" (RAZ-120 usava só cache de sessão do
     * cliente). Mais recentes primeiro; cursor opaco, mesmo envelope de {@code fila}.
     */
    @GetMapping("/empenhos")
    EmpenhosRegistradosResponse empenhos(
            @PathVariable("enteId") UUID enteId,
            @RequestParam(value = "exercicio", required = false) Integer exercicio,
            @RequestParam(value = "dataInicio", required = false) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) LocalDate dataFim,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            Sessao sessao) {
        PaginaEmpenhosRegistrados pagina = consultarEmpenhosRegistrados.executar(
                sessao,
                new TenantId(enteId),
                Optional.ofNullable(exercicio),
                Optional.ofNullable(dataInicio),
                Optional.ofNullable(dataFim),
                Optional.ofNullable(limit),
                Optional.ofNullable(cursor));
        return EmpenhosRegistradosResponse.de(pagina);
    }

    /**
     * RAZ-121: registro completo de liquidações por ente/período — distinto de {@code fila}
     * (aquele é o gate de aprovação, ADR-0029 §1, com segregação da Regra 9; este é "o que já
     * foi lançado", sem recorte de segregação de funções).
     */
    @GetMapping("/liquidacoes/registradas")
    LiquidacoesRegistradasResponse liquidacoesRegistradas(
            @PathVariable("enteId") UUID enteId,
            @RequestParam(value = "dataInicio", required = false) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) LocalDate dataFim,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            Sessao sessao) {
        PaginaLiquidacoesRegistradas pagina = consultarLiquidacoesRegistradas.executar(
                sessao,
                new TenantId(enteId),
                Optional.ofNullable(dataInicio),
                Optional.ofNullable(dataFim),
                Optional.ofNullable(limit),
                Optional.ofNullable(cursor));
        return LiquidacoesRegistradasResponse.de(pagina);
    }

    /** RAZ-121: registro completo de pagamentos por ente/período. Sem beneficiário — PII fica na busca por id. */
    @GetMapping("/pagamentos")
    PagamentosRegistradosResponse pagamentos(
            @PathVariable("enteId") UUID enteId,
            @RequestParam(value = "dataInicio", required = false) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) LocalDate dataFim,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            Sessao sessao) {
        PaginaPagamentosRegistrados pagina = consultarPagamentosRegistrados.executar(
                sessao,
                new TenantId(enteId),
                Optional.ofNullable(dataInicio),
                Optional.ofNullable(dataFim),
                Optional.ofNullable(limit),
                Optional.ofNullable(cursor));
        return PagamentosRegistradosResponse.de(pagina);
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

    record EmpenhosRegistradosResponse(List<EmpenhoRegistradoResponse> itens, String proximoCursor) {

        static EmpenhosRegistradosResponse de(PaginaEmpenhosRegistrados pagina) {
            return new EmpenhosRegistradosResponse(
                    pagina.itens().stream().map(EmpenhoRegistradoResponse::de).toList(),
                    pagina.proximoCursor().orElse(null));
        }
    }

    record EmpenhoRegistradoResponse(
            UUID id,
            long numeroSequencial,
            int exercicio,
            String tipo,
            UUID credorId,
            String valor,
            LocalDate dataFato,
            String historico,
            String status) {

        static EmpenhoRegistradoResponse de(ItemEmpenhoRegistrado item) {
            return new EmpenhoRegistradoResponse(
                    item.id().valor(),
                    item.numeroSequencial(),
                    item.exercicio(),
                    item.tipo().codigo(),
                    item.credorId().valor(),
                    item.valor().valor().toPlainString(),
                    item.dataFato(),
                    item.historico(),
                    item.status().name().toLowerCase());
        }
    }

    record LiquidacoesRegistradasResponse(List<LiquidacaoRegistradaResponse> itens, String proximoCursor) {

        static LiquidacoesRegistradasResponse de(PaginaLiquidacoesRegistradas pagina) {
            return new LiquidacoesRegistradasResponse(
                    pagina.itens().stream().map(LiquidacaoRegistradaResponse::de).toList(),
                    pagina.proximoCursor().orElse(null));
        }
    }

    record LiquidacaoRegistradaResponse(
            UUID id, UUID empenhoId, String valor, LocalDate dataCompetencia, String historico, String statusAprovacao) {

        static LiquidacaoRegistradaResponse de(ItemLiquidacaoRegistrada item) {
            return new LiquidacaoRegistradaResponse(
                    item.id().valor(),
                    item.empenhoId().valor(),
                    item.valor().valor().toPlainString(),
                    item.dataCompetencia(),
                    item.historico(),
                    item.statusAprovacao().name().toLowerCase());
        }
    }

    record PagamentosRegistradosResponse(List<PagamentoRegistradoResponse> itens, String proximoCursor) {

        static PagamentosRegistradosResponse de(PaginaPagamentosRegistrados pagina) {
            return new PagamentosRegistradosResponse(
                    pagina.itens().stream().map(PagamentoRegistradoResponse::de).toList(),
                    pagina.proximoCursor().orElse(null));
        }
    }

    record PagamentoRegistradoResponse(
            UUID id, UUID liquidacaoId, String valor, LocalDate dataCompetencia, String natureza, String historico) {

        static PagamentoRegistradoResponse de(ItemPagamentoRegistrado item) {
            return new PagamentoRegistradoResponse(
                    item.id().valor(),
                    item.liquidacaoId().valor(),
                    item.valor().valor().toPlainString(),
                    item.dataCompetencia(),
                    item.natureza().name().toLowerCase(),
                    item.historico());
        }
    }
}
