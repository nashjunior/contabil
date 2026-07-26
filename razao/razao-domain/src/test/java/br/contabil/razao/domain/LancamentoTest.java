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
    @DisplayName("inversão troca a natureza (D<->C) mantendo conta e valor")
    void inversaoTrocaNatureza() {
        ContaContabilId contaId = ContaContabilId.novo();
        Lancamento debito = Lancamento.de(contaId, Natureza.DEBITO, Dinheiro.de("100.00"));

        Lancamento invertido = debito.inverter();

        assertThat(invertido.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(invertido.contaId()).isEqualTo(contaId);
        assertThat(invertido.valor()).isEqualTo(debito.valor());
        assertThat(invertido.id()).isNotEqualTo(debito.id());
    }
}
