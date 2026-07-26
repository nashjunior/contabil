package br.contabil.execucao.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Agregado de pagamento da despesa: quita total/parcialmente uma liquidação e
 * gera o fato contábil que efetiva a saída financeira.
 */
public final class Pagamento {

    private final PagamentoId id;
    private final TenantId enteId;
    private final LiquidacaoId liquidacaoId;
    private final LocalDate dataCompetencia;
    private final Dinheiro valor;
    private final NaturezaPagamento natureza;
    private final Optional<Beneficiario> beneficiario;
    private final Optional<String> ordemBancaria;
    private final String historico;
    private final UUID fatoContabilId;

    private Pagamento(
            PagamentoId id,
            TenantId enteId,
            LiquidacaoId liquidacaoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            NaturezaPagamento natureza,
            Optional<Beneficiario> beneficiario,
            Optional<String> ordemBancaria,
            String historico,
            UUID fatoContabilId) {
        this.id = id;
        this.enteId = enteId;
        this.liquidacaoId = liquidacaoId;
        this.dataCompetencia = dataCompetencia;
        this.valor = valor;
        this.natureza = natureza;
        this.beneficiario = beneficiario;
        this.ordemBancaria = ordemBancaria;
        this.historico = historico;
        this.fatoContabilId = fatoContabilId;
    }

    public static Pagamento registrar(
            PagamentoId id,
            TenantId enteId,
            LiquidacaoId liquidacaoId,
            LocalDate dataCompetencia,
            Dinheiro valor,
            NaturezaPagamento natureza,
            Optional<Beneficiario> beneficiario,
            Optional<String> ordemBancaria,
            String historico,
            UUID fatoContabilId) {
        validarEntrada(valor, natureza, beneficiario, ordemBancaria, historico);
        return new Pagamento(
                Validacoes.exigirNaoNulo(id, "id"),
                Validacoes.exigirNaoNulo(enteId, "enteId"),
                Validacoes.exigirNaoNulo(liquidacaoId, "liquidacaoId"),
                Validacoes.exigirNaoNulo(dataCompetencia, "dataCompetencia"),
                valor,
                natureza,
                beneficiario,
                ordemBancaria,
                Liquidacao.textoObrigatorio(historico, "histórico"),
                Validacoes.exigirNaoNulo(fatoContabilId, "fatoContabilId"));
    }

    public static void validarEntrada(
            Dinheiro valor,
            NaturezaPagamento natureza,
            Optional<Beneficiario> beneficiario,
            Optional<String> ordemBancaria,
            String historico) {
        Liquidacao.validarValor(valor, "valor do pagamento");
        Validacoes.exigirNaoNulo(natureza, "natureza");
        Validacoes.exigirNaoNulo(beneficiario, "beneficiario");
        Validacoes.exigirNaoNulo(ordemBancaria, "ordemBancaria");
        if (natureza != NaturezaPagamento.FOLHA_CONSOLIDADA && beneficiario.isEmpty()) {
            throw new ExecucaoInvalidaException(
                    "beneficiario_obrigatorio", "pagamento orçamentário exige beneficiário nominal");
        }
        ordemBancaria.ifPresent(valorOrdem -> Liquidacao.textoObrigatorio(valorOrdem, "ordem bancária"));
        Liquidacao.textoObrigatorio(historico, "histórico");
    }

    public PagamentoId id() {
        return id;
    }

    public TenantId enteId() {
        return enteId;
    }

    public LiquidacaoId liquidacaoId() {
        return liquidacaoId;
    }

    public LocalDate dataCompetencia() {
        return dataCompetencia;
    }

    public Dinheiro valor() {
        return valor;
    }

    public NaturezaPagamento natureza() {
        return natureza;
    }

    public Optional<Beneficiario> beneficiario() {
        return beneficiario;
    }

    public Optional<String> ordemBancaria() {
        return ordemBancaria;
    }

    public String historico() {
        return historico;
    }

    public UUID fatoContabilId() {
        return fatoContabilId;
    }
}
