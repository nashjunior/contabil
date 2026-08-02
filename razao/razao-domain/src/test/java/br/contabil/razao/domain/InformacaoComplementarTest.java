package br.contabil.razao.domain;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InformacaoComplementarTest {

    @Test
    @DisplayName("são exatamente as 9 IC da MSC")
    void saoNoveIc() {
        assertThat(InformacaoComplementar.values()).hasSize(9);
    }

    @Test
    @DisplayName("FP e DC são derivadas (não capturadas); as demais são capturadas")
    void fpEDcSaoDerivadas() {
        assertThat(InformacaoComplementar.FP.capturada()).isFalse();
        assertThat(InformacaoComplementar.DC.capturada()).isFalse();
        assertThat(InformacaoComplementar.FP.origem()).isEqualTo(InformacaoComplementar.Origem.DERIVADA);

        Arrays.stream(InformacaoComplementar.values())
                .filter(ic -> ic != InformacaoComplementar.FP && ic != InformacaoComplementar.DC)
                .forEach(ic -> assertThat(ic.capturada()).isTrue());
    }

    @Test
    @DisplayName("PO é fato-wide; FR/CO/NR/ND/FS/AI são de partida")
    void classificacaoDaOrigem() {
        assertThat(InformacaoComplementar.PO.origem()).isEqualTo(InformacaoComplementar.Origem.FATO);
        for (InformacaoComplementar ic : new InformacaoComplementar[] {
            InformacaoComplementar.FR,
            InformacaoComplementar.CO,
            InformacaoComplementar.NR,
            InformacaoComplementar.ND,
            InformacaoComplementar.FS,
            InformacaoComplementar.AI
        }) {
            assertThat(ic.origem()).isEqualTo(InformacaoComplementar.Origem.LANCAMENTO);
        }
    }
}
