package br.contabil.razao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstornarFatoContabilTest {

    @Mock
    private FatoContabilRepository repositorio;

    @Mock
    private ContadorFatoPort contadorFato;

    @Mock
    private PeriodoContabilPort periodoContabil;

    private EstornarFatoContabil useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final UUID periodoId = UUID.randomUUID();
    private final Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new EstornarFatoContabil(repositorio, contadorFato, periodoContabil, relogioFixo);
    }

    @Test
    @DisplayName("estorna um fato existente criando um NOVO fato com lançamentos invertidos")
    void estornaFatoExistente() {
        FatoContabil original = FatoContabil.registrar(
                enteId,
                1L,
                LocalDate.of(2026, 7, 1),
                periodoId,
                TipoEvento.RECEITA,
                "original",
                "origem",
                List.of(
                        Lancamento.de(UUID.randomUUID(), Natureza.DEBITO, Dinheiro.de("300.00")),
                        Lancamento.de(UUID.randomUUID(), Natureza.CREDITO, Dinheiro.de("300.00"))),
                relogioFixo);

        when(repositorio.buscarPorId(enteId, original.id())).thenReturn(Optional.of(original));
        when(periodoContabil.periodoAbertoPara(enteId, LocalDate.of(2026, 7, 19))).thenReturn(periodoId);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(2L);

        FatoContabil estorno = useCase.executar(
                enteId, original.id(), LocalDate.of(2026, 7, 19), "correção", "origem");

        assertThat(estorno.isEstorno()).isTrue();
        assertThat(estorno.fatoEstornadoId()).isEqualTo(original.id());
        assertThat(estorno.numeroSeq()).isEqualTo(2L);
    }

    @Test
    @DisplayName("rejeita estorno de fato inexistente")
    void rejeitaFatoInexistente() {
        UUID idInexistente = UUID.randomUUID();
        when(repositorio.buscarPorId(enteId, idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        useCase.executar(enteId, idInexistente, LocalDate.of(2026, 7, 19), "correção", "origem"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
