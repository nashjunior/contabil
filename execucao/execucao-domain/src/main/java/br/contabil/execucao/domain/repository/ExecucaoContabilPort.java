package br.contabil.execucao.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
 */
public interface ExecucaoContabilPort {

    /** RAZ-66: escritura o empenho (D Crédito Empenhado a Liquidar / C Crédito Disponível, ADR-0021). */
    ReferenciaFatoContabil registrarEmpenho(SolicitacaoEscrituracaoEmpenho solicitacao);

    ReferenciaFatoContabil registrarLiquidacao(SolicitacaoEscrituracaoLiquidacao solicitacao);

    ReferenciaFatoContabil registrarPagamento(SolicitacaoEscrituracaoPagamento solicitacao);

    record SolicitacaoEscrituracaoEmpenho(
            TenantId enteId, EmpenhoId empenhoId, LocalDate dataFato, Dinheiro valor, String historico) {

        public SolicitacaoEscrituracaoEmpenho {
            Objects.requireNonNull(enteId, "enteId não pode ser nulo");
            Objects.requireNonNull(empenhoId, "empenhoId não pode ser nulo");
            Objects.requireNonNull(dataFato, "dataFato não pode ser nula");
            Objects.requireNonNull(valor, "valor não pode ser nulo");
            Objects.requireNonNull(historico, "histórico não pode ser nulo");
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
            Objects.requireNonNull(enteId, "enteId não pode ser nulo");
            Objects.requireNonNull(liquidacaoId, "liquidacaoId não pode ser nulo");
            Objects.requireNonNull(empenhoId, "empenhoId não pode ser nulo");
            Objects.requireNonNull(dataCompetencia, "dataCompetencia não pode ser nula");
            Objects.requireNonNull(valor, "valor não pode ser nulo");
            Objects.requireNonNull(documentosSuporte, "documentosSuporte não pode ser nulo");
            Objects.requireNonNull(historico, "histórico não pode ser nulo");
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
            Objects.requireNonNull(enteId, "enteId não pode ser nulo");
            Objects.requireNonNull(pagamentoId, "pagamentoId não pode ser nulo");
            Objects.requireNonNull(liquidacaoId, "liquidacaoId não pode ser nulo");
            Objects.requireNonNull(dataCompetencia, "dataCompetencia não pode ser nula");
            Objects.requireNonNull(valor, "valor não pode ser nulo");
            Objects.requireNonNull(natureza, "natureza não pode ser nula");
            Objects.requireNonNull(beneficiario, "beneficiario não pode ser nulo");
            Objects.requireNonNull(ordemBancaria, "ordemBancaria não pode ser nula");
            Objects.requireNonNull(historico, "histórico não pode ser nulo");
        }
    }
}
