package br.contabil.execucao.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import br.contabil.execucao.domain.Beneficiario;
import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Port de escrituração contábil produzido pela execução.
 *
 * <p>A implementação traduz estas solicitações para o motor do razão sem criar
 * dependência direta de {@code execucao} para {@code razao}. O fato contábil
 * continua all-or-nothing: se a escrituração falhar, o use case não persiste o
 * estágio da despesa.
 *
 * <p>Os três métodos devolvem {@link ReferenciaFatoContabil} (ADR-0028), o VO
 * de referência do módulo consumidor — não {@code UUID} cru. O {@code UUID}
 * de fio fica contido no {@code ExecucaoContabilPortAdapter} (bootstrap),
 * única classe que conhece tanto {@code execucao} quanto {@code razao}.
 *
 * <p>{@code fonteRecurso} (FR) e {@code classificacaoOrcamentaria} (CO) são
 * informações complementares (IC) da MSC capturadas aqui, na origem, e
 * propagadas ao razão como dimensões de partida (ADR-0050/ADR-0057) — nunca
 * reconstruídas por heurística depois. A liquidação e o pagamento não
 * recebem estes campos porque o adapter os resolve a partir do empenho de
 * origem (mesma classificação do empenho, ADR-0057).
 */
public interface ExecucaoContabilPort {

    /** RAZ-66: escritura o empenho (D Crédito Empenhado a Liquidar / C Crédito Disponível, ADR-0021). */
    ReferenciaFatoContabil registrarEmpenho(SolicitacaoEscrituracaoEmpenho solicitacao);

    ReferenciaFatoContabil registrarLiquidacao(SolicitacaoEscrituracaoLiquidacao solicitacao);

    ReferenciaFatoContabil registrarPagamento(SolicitacaoEscrituracaoPagamento solicitacao);

    record SolicitacaoEscrituracaoEmpenho(
            TenantId enteId, EmpenhoId empenhoId, LocalDate dataFato, Dinheiro valor, String historico,
            String fonteRecurso, String classificacaoOrcamentaria) {

        public SolicitacaoEscrituracaoEmpenho {
            Validacoes.exigirNaoNulo(enteId, "enteId");
            Validacoes.exigirNaoNulo(empenhoId, "empenhoId");
            Validacoes.exigirNaoNulo(dataFato, "dataFato");
            Validacoes.exigirNaoNulo(valor, "valor");
            Validacoes.exigirNaoNulo(historico, "histórico");
            Validacoes.exigirNaoNulo(classificacaoOrcamentaria, "classificacaoOrcamentaria");
            // fonteRecurso nullable: null = ente sem vinculação (sem DDR)
        }
    }

    record SolicitacaoEscrituracaoLiquidacao(
            TenantId enteId,
            LiquidacaoId liquidacaoId,
            EmpenhoId empenhoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            List<DocumentoSuporte> documentosSuporte,
            String historico) {

        public SolicitacaoEscrituracaoLiquidacao {
            Validacoes.exigirNaoNulo(enteId, "enteId");
            Validacoes.exigirNaoNulo(liquidacaoId, "liquidacaoId");
            Validacoes.exigirNaoNulo(empenhoId, "empenhoId");
            Validacoes.exigirNaoNulo(dataCompetencia, "dataCompetencia");
            Validacoes.exigirNaoNulo(valor, "valor");
            Validacoes.exigirNaoNulo(documentosSuporte, "documentosSuporte");
            Validacoes.exigirNaoNulo(historico, "histórico");
            documentosSuporte = List.copyOf(documentosSuporte);
        }
    }

    record SolicitacaoEscrituracaoPagamento(
            TenantId enteId,
            PagamentoId pagamentoId,
            LiquidacaoId liquidacaoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            NaturezaPagamento natureza,
            Optional<Beneficiario> beneficiario,
            Optional<String> ordemBancaria,
            String historico) {

        public SolicitacaoEscrituracaoPagamento {
            Validacoes.exigirNaoNulo(enteId, "enteId");
            Validacoes.exigirNaoNulo(pagamentoId, "pagamentoId");
            Validacoes.exigirNaoNulo(liquidacaoId, "liquidacaoId");
            Validacoes.exigirNaoNulo(dataCompetencia, "dataCompetencia");
            Validacoes.exigirNaoNulo(valor, "valor");
            Validacoes.exigirNaoNulo(natureza, "natureza");
            Validacoes.exigirNaoNulo(beneficiario, "beneficiario");
            Validacoes.exigirNaoNulo(ordemBancaria, "ordemBancaria");
            Validacoes.exigirNaoNulo(historico, "histórico");
        }
    }
}
