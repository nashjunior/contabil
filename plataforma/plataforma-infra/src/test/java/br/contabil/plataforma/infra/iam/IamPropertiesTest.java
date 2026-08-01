package br.contabil.plataforma.infra.iam;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;

/**
 * Trava a matriz RBAC de {@link IamProperties.Papel} (doc "fluxo-execucao-operador-contrato-api"
 * §2/§5.3/§6.6, [ADR-0023](../../../../../../../docs/arquitetura-tecnica/adr/0023-gate-aprovacao-pagamento-segregacao.md),
 * Regra 9 "quem lança não autoriza; quem autoriza não paga"). Nenhum outro teste JVM exercitava
 * {@link IamProperties.Papel#permite} direto — todos substituem {@code ServicoIdentidade} por um
 * duplo que sempre autoriza (RAZ-222).
 */
class IamPropertiesTest {

    private static final Recurso EMPENHO = new Recurso("execucao:empenho");
    private static final Recurso LIQUIDACAO = new Recurso("execucao:liquidacao");
    private static final Recurso PAGAMENTO = new Recurso("execucao:pagamento");
    private static final Recurso FATO_CONTABIL = new Recurso("razao:fato_contabil");

    @Test
    @DisplayName("LANCADOR lança empenho e liquidação, mas nunca aprova nem paga (RAZ-222)")
    void lancadorLancaMasNaoAprovaNemPaga() {
        var lancador = IamProperties.Papel.LANCADOR;

        assertThat(lancador.permite(EMPENHO, Acao.CRIAR)).isTrue();
        assertThat(lancador.permite(LIQUIDACAO, Acao.CRIAR)).isTrue();
        assertThat(lancador.permite(FATO_CONTABIL, Acao.CRIAR)).isTrue();
        assertThat(lancador.permite(FATO_CONTABIL, Acao.ESTORNAR)).isTrue();

        assertThat(lancador.permite(PAGAMENTO, Acao.APROVAR)).isFalse();
        assertThat(lancador.permite(PAGAMENTO, Acao.CRIAR)).isFalse();
        assertThat(lancador.permite(EMPENHO, Acao.ASSINAR)).isFalse();
    }

    @Test
    @DisplayName("AUTORIZADOR aprova o gate de pagamento (ordenador), nunca lança nem paga")
    void autorizadorAprovaMasNaoLancaNemPaga() {
        var autorizador = IamProperties.Papel.AUTORIZADOR;

        assertThat(autorizador.permite(PAGAMENTO, Acao.APROVAR)).isTrue();
        assertThat(autorizador.permite(EMPENHO, Acao.ASSINAR)).isTrue();
        assertThat(autorizador.permite(FATO_CONTABIL, Acao.APROVAR)).isTrue();
        assertThat(autorizador.permite(FATO_CONTABIL, Acao.ASSINAR)).isTrue();

        assertThat(autorizador.permite(LIQUIDACAO, Acao.CRIAR)).isFalse();
        assertThat(autorizador.permite(EMPENHO, Acao.CRIAR)).isFalse();
        assertThat(autorizador.permite(PAGAMENTO, Acao.CRIAR)).isFalse();
    }

    @Test
    @DisplayName("PAGADOR efetiva a baixa financeira, mas nunca aprova a própria ordem (Regra 9)")
    void pagadorPagaMasNaoAprova() {
        var pagador = IamProperties.Papel.PAGADOR;

        assertThat(pagador.permite(PAGAMENTO, Acao.CRIAR)).isTrue();
        assertThat(pagador.permite(PAGAMENTO, Acao.ASSINAR)).isTrue();

        assertThat(pagador.permite(PAGAMENTO, Acao.APROVAR)).isFalse();
        assertThat(pagador.permite(LIQUIDACAO, Acao.CRIAR)).isFalse();
        assertThat(pagador.permite(EMPENHO, Acao.CRIAR)).isFalse();
    }

    @Test
    @DisplayName("AUTORIZADOR+PAGADOR concedidos juntos ainda cobrem lançar->aprovar->pagar com atores distintos")
    void fluxoCompletoExigeTresAtoresDistintos() {
        // Regra 9 / ADR-0023: nenhum papel isolado cobre os três verbos sobre a mesma
        // liquidação-pagamento — a segregação vive na própria matriz, não só na validação
        // de concessão conflitante.
        for (IamProperties.Papel papel : IamProperties.Papel.values()) {
            boolean cria = papel.permite(LIQUIDACAO, Acao.CRIAR);
            boolean aprova = papel.permite(PAGAMENTO, Acao.APROVAR);
            boolean paga = papel.permite(PAGAMENTO, Acao.CRIAR);

            long quantosVerbos = (cria ? 1 : 0) + (aprova ? 1 : 0) + (paga ? 1 : 0);
            assertThat(quantosVerbos)
                    .withFailMessage(
                            "papel %s concede mais de um verbo do ciclo liquidação->aprovação->pagamento (%s/%s/%s)",
                            papel, cria, aprova, paga)
                    .isLessThanOrEqualTo(1);
        }
    }
}
