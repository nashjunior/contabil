package br.contabil.razao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.Dinheiro;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LancamentoTest {

    @Test
    @DisplayName("rejeita valor zero ou negativo")
    void rejeitaValorNaoPositivo() {
        UUID contaId = UUID.randomUUID();

        assertThatThrownBy(() -> Lancamento.de(contaId, Natureza.DEBITO, Dinheiro.zero()))
                .isInstanceOf(LancamentoInvalidoException.class);
        assertThatThrownBy(() -> Lancamento.de(contaId, Natureza.DEBITO, Dinheiro.de("-10.00")))
                .isInstanceOf(LancamentoInvalidoException.class);
    }

    @Test
    @DisplayName("inversão troca a natureza (D<->C) mantendo conta e valor")
    void inversaoTrocaNatureza() {
        UUID contaId = UUID.randomUUID();
        Lancamento debito = Lancamento.de(contaId, Natureza.DEBITO, Dinheiro.de("100.00"));

        Lancamento invertido = debito.inverter();

        assertThat(invertido.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(invertido.contaId()).isEqualTo(contaId);
        assertThat(invertido.valor()).isEqualTo(debito.valor());
        assertThat(invertido.id()).isNotEqualTo(debito.id());
    }
}
