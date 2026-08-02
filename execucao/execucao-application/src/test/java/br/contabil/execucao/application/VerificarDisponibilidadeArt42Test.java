package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.DisponibilidadeArt42InsuficienteException;
import br.contabil.execucao.domain.JanelaMandato;
import br.contabil.execucao.domain.repository.DisponibilidadeArt42Port;
import br.contabil.execucao.domain.repository.JanelaMandatoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

@ExtendWith(MockitoExtension.class)
class VerificarDisponibilidadeArt42Test {

    @Mock
    private JanelaMandatoPort janelaMandato;

    @Mock
    private DisponibilidadeArt42Port disponibilidade;

    private VerificarDisponibilidadeArt42 gate;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final JanelaMandato mandato = new JanelaMandato(LocalDate.of(2025, 1, 1), LocalDate.of(2028, 12, 31));
    private final LocalDate dataDentroDaJanela = LocalDate.of(2028, 6, 1);
    private final LocalDate dataForaDaJanela = LocalDate.of(2026, 6, 1);

    @BeforeEach
    void setUp() {
        gate = new VerificarDisponibilidadeArt42(janelaMandato, disponibilidade);
    }

    @Test
    @DisplayName("sem fonte de recursos, não consulta nada — ente sem vinculação não tem gate")
    void semFonteNaoConsultaNada() {
        assertThatCode(() -> gate.verificar(enteId, dataDentroDaJanela, null, Dinheiro.de("100.00")))
                .doesNotThrowAnyException();
        verifyNoInteractions(janelaMandato, disponibilidade);
    }

    @Test
    @DisplayName("sem mandato configurado, não bloqueia (monitor, não gate)")
    void semMandatoConfiguradoNaoBloqueia() {
        when(janelaMandato.buscar(enteId)).thenReturn(Optional.empty());

        assertThatCode(() -> gate.verificar(enteId, dataDentroDaJanela, "0100000000", Dinheiro.de("100.00")))
                .doesNotThrowAnyException();
        verifyNoInteractions(disponibilidade);
    }

    @Test
    @DisplayName("fora dos dois últimos quadrimestres do mandato, não bloqueia mesmo com disponibilidade insuficiente")
    void foraDaJanelaNaoBloqueia() {
        when(janelaMandato.buscar(enteId)).thenReturn(Optional.of(mandato));

        assertThatCode(() -> gate.verificar(enteId, dataForaDaJanela, "0100000000", Dinheiro.de("100.00")))
                .doesNotThrowAnyException();
        verifyNoInteractions(disponibilidade);
    }

    @Test
    @DisplayName("dentro da janela, sem dado de disponibilidade apurável, não bloqueia")
    void dentroDaJanelaSemDadoDeDisponibilidadeNaoBloqueia() {
        when(janelaMandato.buscar(enteId)).thenReturn(Optional.of(mandato));
        when(disponibilidade.saldoDisponivel(enteId, "0100000000")).thenReturn(Optional.empty());

        assertThatCode(() -> gate.verificar(enteId, dataDentroDaJanela, "0100000000", Dinheiro.de("100.00")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dentro da janela, disponibilidade cobre o valor: prossegue")
    void dentroDaJanelaComDisponibilidadeSuficienteProssegue() {
        when(janelaMandato.buscar(enteId)).thenReturn(Optional.of(mandato));
        when(disponibilidade.saldoDisponivel(enteId, "0100000000")).thenReturn(Optional.of(Dinheiro.de("500.00")));

        assertThatCode(() -> gate.verificar(enteId, dataDentroDaJanela, "0100000000", Dinheiro.de("500.00")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dentro da janela, disponibilidade insuficiente: recusa com disponibilidade_art42_insuficiente")
    void dentroDaJanelaComDisponibilidadeInsuficienteRecusa() {
        when(janelaMandato.buscar(enteId)).thenReturn(Optional.of(mandato));
        when(disponibilidade.saldoDisponivel(enteId, "0100000000")).thenReturn(Optional.of(Dinheiro.de("50.00")));

        assertThatThrownBy(() -> gate.verificar(enteId, dataDentroDaJanela, "0100000000", Dinheiro.de("100.00")))
                .isInstanceOf(DisponibilidadeArt42InsuficienteException.class)
                .hasMessageContaining("0100000000")
                .extracting(e -> ((DisponibilidadeArt42InsuficienteException) e).codigo())
                .isEqualTo("disponibilidade_art42_insuficiente");
    }

    @Test
    @DisplayName("sem compensação entre fontes: consulta é escopada à fonte do empenho, nunca a outras")
    void consultaEscopadaAFonteInformada() {
        when(janelaMandato.buscar(enteId)).thenReturn(Optional.of(mandato));
        when(disponibilidade.saldoDisponivel(any(), any())).thenReturn(Optional.of(Dinheiro.de("500.00")));

        gate.verificar(enteId, dataDentroDaJanela, "0200000000", Dinheiro.de("100.00"));

        org.mockito.Mockito.verify(disponibilidade).saldoDisponivel(enteId, "0200000000");
        org.mockito.Mockito.verifyNoMoreInteractions(disponibilidade);
    }
}
