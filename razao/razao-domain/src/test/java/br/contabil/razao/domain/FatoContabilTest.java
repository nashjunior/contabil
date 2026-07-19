package br.contabil.razao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FatoContabilTest {

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final UUID periodoId = UUID.randomUUID();
    private final UUID contaCaixa = UUID.randomUUID();
    private final UUID contaReceita = UUID.randomUUID();
    private final Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);

    private List<Lancamento> lancamentosBalanceados() {
        return List.of(
                Lancamento.de(contaCaixa, Natureza.DEBITO, Dinheiro.de("1000.00")),
                Lancamento.de(contaReceita, Natureza.CREDITO, Dinheiro.de("1000.00")));
    }

    @Test
    @DisplayName("registra um fato balanceado com data/hora do relógio injetado, nunca de input")
    void registraFatoBalanceado() {
        FatoContabil fato = FatoContabil.registrar(
                enteId,
                1L,
                LocalDate.of(2026, 7, 1),
                periodoId,
                TipoEvento.RECEITA,
                "Arrecadação de tributo X",
                "modulo-receita",
                lancamentosBalanceados(),
                relogioFixo);

        assertThat(fato.numeroSeq()).isEqualTo(1L);
        assertThat(fato.dataHoraRegistro()).isEqualTo(relogioFixo.instant().atZone(ZoneOffset.UTC).toLocalDateTime());
        assertThat(fato.lancamentos()).hasSize(2);
        assertThat(fato.isEstorno()).isFalse();
    }

    @Test
    @DisplayName("Regra 8: rejeita fato com soma(D) != soma(C), sem persistir nada")
    void rejeitaPartidasNaoBalanceadas() {
        List<Lancamento> desbalanceado = List.of(
                Lancamento.de(contaCaixa, Natureza.DEBITO, Dinheiro.de("1000.00")),
                Lancamento.de(contaReceita, Natureza.CREDITO, Dinheiro.de("900.00")));

        assertThatThrownBy(() -> FatoContabil.registrar(
                        enteId,
                        1L,
                        LocalDate.of(2026, 7, 1),
                        periodoId,
                        TipoEvento.RECEITA,
                        "historico",
                        "origem",
                        desbalanceado,
                        relogioFixo))
                .isInstanceOf(PartidasNaoBalanceadasException.class);
    }

    @Test
    @DisplayName("rejeita fato com menos de 2 lançamentos")
    void rejeitaFatoComUmSoLancamento() {
        List<Lancamento> umSo = List.of(Lancamento.de(contaCaixa, Natureza.DEBITO, Dinheiro.de("100.00")));

        assertThatThrownBy(() -> FatoContabil.registrar(
                        enteId, 1L, LocalDate.of(2026, 7, 1), periodoId, TipoEvento.RECEITA,
                        "historico", "origem", umSo, relogioFixo))
                .isInstanceOf(PartidasNaoBalanceadasException.class);
    }

    @Test
    @DisplayName("lista de lançamentos do fato é imutável (fato consolidado não muta)")
    void lancamentosImutaveis() {
        FatoContabil fato = FatoContabil.registrar(
                enteId, 1L, LocalDate.of(2026, 7, 1), periodoId, TipoEvento.RECEITA,
                "historico", "origem", lancamentosBalanceados(), relogioFixo);

        assertThatThrownBy(() -> fato.lancamentos()
                        .add(Lancamento.de(contaCaixa, Natureza.DEBITO, Dinheiro.de("1.00"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("estorno gera lançamentos invertidos (D<->C) que neutralizam o fato original")
    void estornoNeutralizaFatoOriginal() {
        FatoContabil original = FatoContabil.registrar(
                enteId, 1L, LocalDate.of(2026, 7, 1), periodoId, TipoEvento.RECEITA,
                "historico original", "origem", lancamentosBalanceados(), relogioFixo);

        FatoContabil estorno = FatoContabil.estornar(
                original, 2L, LocalDate.of(2026, 7, 2), periodoId, "correção do fato 1", "origem", relogioFixo);

        assertThat(estorno.isEstorno()).isTrue();
        assertThat(estorno.fatoEstornadoId()).isEqualTo(original.id());
        assertThat(estorno.tipoEvento()).isEqualTo(TipoEvento.ESTORNO);
        // soma dos dois fatos, conta a conta, é zero: efeito líquido neutralizado.
        for (int i = 0; i < original.lancamentos().size(); i++) {
            Lancamento doOriginal = original.lancamentos().get(i);
            Lancamento doEstorno = estorno.lancamentos().get(i);
            assertThat(doEstorno.contaId()).isEqualTo(doOriginal.contaId());
            assertThat(doEstorno.valor()).isEqualTo(doOriginal.valor());
            assertThat(doEstorno.natureza()).isEqualTo(doOriginal.natureza().inversa());
        }
        // o próprio estorno também fecha Σ=Σ (é só mais um fato).
        FatoContabil.validarPartidasDobradas(estorno.lancamentos());
    }
}
