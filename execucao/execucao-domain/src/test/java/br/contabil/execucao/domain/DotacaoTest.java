package br.contabil.execucao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DotacaoTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final UUID unidadeGestoraId = UUID.randomUUID();

    @Test
    @DisplayName("Fixação: carga da LOA registra a dotação vinculada ao exercício")
    void registraDotacaoValida() {
        Dotacao dotacao = Dotacao.registrar(
                DotacaoId.novo(),
                enteId,
                2026,
                "04.122.0001.2001",
                "0100000000",
                unidadeGestoraId,
                Dinheiro.de("100000.00"));

        assertThat(dotacao.exercicio()).isEqualTo(2026);
        assertThat(dotacao.classificacaoOrcamentaria()).isEqualTo("04.122.0001.2001");
        assertThat(dotacao.fonteRecurso()).isEqualTo("0100000000");
        assertThat(dotacao.unidadeGestoraId()).isEqualTo(unidadeGestoraId);
        assertThat(dotacao.valorAutorizado()).isEqualTo(Dinheiro.de("100000.00"));
    }

    @Test
    @DisplayName("art. 34: valorAutorizado da dotação precisa ser positivo")
    void rejeitaValorAutorizadoZero() {
        assertThatThrownBy(() -> Dotacao.registrar(
                        DotacaoId.novo(),
                        enteId,
                        2026,
                        "04.122.0001.2001",
                        "0100000000",
                        unidadeGestoraId,
                        Dinheiro.zero()))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    @DisplayName("rejeita valorAutorizado negativo antes de qualquer I/O")
    void rejeitaValorAutorizadoNegativo() {
        assertThatThrownBy(() -> Dotacao.registrar(
                        DotacaoId.novo(),
                        enteId,
                        2026,
                        "04.122.0001.2001",
                        "0100000000",
                        unidadeGestoraId,
                        Dinheiro.de("-1.00")))
                .isInstanceOf(ExecucaoInvalidaException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    @DisplayName("rejeita classificação orçamentária em branco")
    void rejeitaClassificacaoEmBranco() {
        assertThatThrownBy(() -> Dotacao.registrar(
                        DotacaoId.novo(), enteId, 2026, "   ", "0100000000", unidadeGestoraId, Dinheiro.de("100.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita fonte de recurso em branco")
    void rejeitaFonteRecursoEmBranco() {
        assertThatThrownBy(() -> Dotacao.registrar(
                        DotacaoId.novo(),
                        enteId,
                        2026,
                        "04.122.0001.2001",
                        " ",
                        unidadeGestoraId,
                        Dinheiro.de("100.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("duas dotações com o mesmo id são iguais (identidade do agregado)")
    void igualdadePorId() {
        DotacaoId id = DotacaoId.novo();
        Dotacao primeira = Dotacao.registrar(
                id, enteId, 2026, "04.122.0001.2001", "0100000000", unidadeGestoraId, Dinheiro.de("100.00"));
        Dotacao segunda = Dotacao.registrar(
                id, enteId, 2026, "04.122.0001.2001", "0100000000", unidadeGestoraId, Dinheiro.de("999.00"));

        assertThat(primeira).isEqualTo(segunda);
        assertThat(primeira).hasSameHashCodeAs(segunda);
    }
}
