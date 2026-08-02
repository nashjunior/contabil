package br.contabil.razao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.Dinheiro;

class LancamentoTest {

    @Test
    @DisplayName("rejeita valor zero ou negativo")
    void rejeitaValorNaoPositivo() {
        ContaContabilId contaId = ContaContabilId.novo();
        Dinheiro zero = Dinheiro.zero();
        Dinheiro negativo = Dinheiro.de("-10.00");

        assertThatThrownBy(() -> Lancamento.de(contaId, Natureza.DEBITO, zero))
                .isInstanceOf(LancamentoInvalidoException.class);
        assertThatThrownBy(() -> Lancamento.de(contaId, Natureza.DEBITO, negativo))
                .isInstanceOf(LancamentoInvalidoException.class);
    }

    @Test
    @DisplayName("inversão troca a natureza (D<->C) mantendo conta, valor e fonte de recursos")
    void inversaoTrocaNatureza() {
        ContaContabilId contaId = ContaContabilId.novo();
        Lancamento debito =
                Lancamento.de(contaId, Natureza.DEBITO, Dinheiro.de("100.00"), FonteRecurso.de("1500"));

        Lancamento invertido = debito.inverter();

        assertThat(invertido.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(invertido.contaId()).isEqualTo(contaId);
        assertThat(invertido.valor()).isEqualTo(debito.valor());
        assertThat(invertido.id()).isNotEqualTo(debito.id());
        assertThat(invertido.fonteRecurso()).contains(FonteRecurso.de("1500"));
    }

    @Test
    @DisplayName("fonte de recursos é opcional: ausente pela fábrica de 3 argumentos, presente pela de 4")
    void fonteDeRecursosOpcional() {
        ContaContabilId contaId = ContaContabilId.novo();

        Lancamento semFonte = Lancamento.de(contaId, Natureza.DEBITO, Dinheiro.de("50.00"));
        assertThat(semFonte.fonteRecurso()).isEmpty();

        Lancamento comFonte =
                Lancamento.de(contaId, Natureza.CREDITO, Dinheiro.de("50.00"), FonteRecurso.de("1500"));
        assertThat(comFonte.fonteRecurso()).contains(FonteRecurso.de("1500"));

        Lancamento fonteNula = Lancamento.de(contaId, Natureza.CREDITO, Dinheiro.de("50.00"), (FonteRecurso) null);
        assertThat(fonteNula.fonteRecurso()).isEmpty();
    }

    @Test
    @DisplayName("carrega as IC de partida (FR/CO/NR/ND/FS/AI) e as preserva na inversão")
    void carregaInformacoesComplementaresEPreservaNaInversao() {
        ContaContabilId contaId = ContaContabilId.novo();
        InformacoesComplementares ic = InformacoesComplementares.de(
                FonteRecurso.de("1500"),
                ExecucaoOrcamentaria.de("2"),
                NaturezaReceita.de("11130111"),
                NaturezaDespesa.de("33903000"),
                FuncaoSubfuncao.de("04122"),
                AnoInscricaoRp.de(2025));

        Lancamento debito = Lancamento.de(contaId, Natureza.DEBITO, Dinheiro.de("100.00"), ic);

        assertThat(debito.informacoesComplementares()).isEqualTo(ic);
        assertThat(debito.fonteRecurso()).contains(FonteRecurso.de("1500"));

        Lancamento invertido = debito.inverter();
        assertThat(invertido.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(invertido.informacoesComplementares()).isEqualTo(ic);
    }

    @Test
    @DisplayName("sem IC, o lançamento carrega o VO 'nenhuma' (nunca nulo)")
    void semIcCarregaNenhuma() {
        Lancamento semIc = Lancamento.de(ContaContabilId.novo(), Natureza.DEBITO, Dinheiro.de("10.00"));
        assertThat(semIc.informacoesComplementares()).isEqualTo(InformacoesComplementares.nenhuma());
        assertThat(semIc.informacoesComplementares().vazia()).isTrue();
    }
}
