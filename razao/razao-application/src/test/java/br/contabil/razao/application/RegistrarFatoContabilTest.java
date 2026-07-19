package br.contabil.razao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PartidasNaoBalanceadasException;
import br.contabil.razao.domain.PeriodoEncerradoException;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrarFatoContabilTest {

    @Mock
    private FatoContabilRepository repositorio;

    @Mock
    private ContadorFatoPort contadorFato;

    @Mock
    private PeriodoContabilPort periodoContabil;

    private RegistrarFatoContabil useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final UUID periodoId = UUID.randomUUID();
    private final LocalDate dataCompetencia = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);
        useCase = new RegistrarFatoContabil(repositorio, contadorFato, periodoContabil, relogioFixo);
    }

    private List<Lancamento> lancamentosBalanceados() {
        return List.of(
                Lancamento.de(UUID.randomUUID(), Natureza.DEBITO, Dinheiro.de("500.00")),
                Lancamento.de(UUID.randomUUID(), Natureza.CREDITO, Dinheiro.de("500.00")));
    }

    @Test
    @DisplayName("obtém período aberto e numero_seq, monta o fato e insere no repositório")
    void registraComSucesso() {
        when(periodoContabil.periodoAbertoPara(enteId, dataCompetencia)).thenReturn(periodoId);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(42L);

        FatoContabil fato = useCase.executar(
                enteId, dataCompetencia, TipoEvento.RECEITA, "historico", "origem", lancamentosBalanceados());

        assertThat(fato.numeroSeq()).isEqualTo(42L);
        assertThat(fato.periodoId()).isEqualTo(periodoId);
        verify(repositorio).inserir(fato);
    }

    @Test
    @DisplayName("falha rápido em Σdébito != Σcrédito sem consultar período/contador/repositório")
    void falhaRapidoSemBalancear() {
        List<Lancamento> desbalanceado = List.of(
                Lancamento.de(UUID.randomUUID(), Natureza.DEBITO, Dinheiro.de("500.00")),
                Lancamento.de(UUID.randomUUID(), Natureza.CREDITO, Dinheiro.de("400.00")));

        assertThatThrownBy(() -> useCase.executar(
                        enteId, dataCompetencia, TipoEvento.RECEITA, "historico", "origem", desbalanceado))
                .isInstanceOf(PartidasNaoBalanceadasException.class);

        verify(periodoContabil, never()).periodoAbertoPara(any(), any());
        verify(contadorFato, never()).proximoNumeroSeq(any());
        verify(repositorio, never()).inserir(any());
    }

    @Test
    @DisplayName("Regra 5: período encerrado rejeita o registro antes de obter numero_seq")
    void rejeitaPeriodoEncerrado() {
        when(periodoContabil.periodoAbertoPara(enteId, dataCompetencia))
                .thenThrow(new PeriodoEncerradoException(enteId, dataCompetencia));

        assertThatThrownBy(() -> useCase.executar(
                        enteId,
                        dataCompetencia,
                        TipoEvento.RECEITA,
                        "historico",
                        "origem",
                        lancamentosBalanceados()))
                .isInstanceOf(PeriodoEncerradoException.class);

        verify(contadorFato, never()).proximoNumeroSeq(any());
        verify(repositorio, never()).inserir(any());
    }
}
