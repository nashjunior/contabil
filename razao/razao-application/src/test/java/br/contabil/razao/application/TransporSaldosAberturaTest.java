package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

@ExtendWith(MockitoExtension.class)
class TransporSaldosAberturaTest {

    @Mock
    private FatoContabilRepository repositorio;

    @Mock
    private ContadorFatoPort contadorFato;

    @Mock
    private ConsultaSaldoPort consultaSaldo;

    @Mock
    private PeriodoContabilPort periodoContabil;

    @Mock
    private AuditoriaEscrita auditoria;

    private TransporSaldosAbertura abertura;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final PeriodoContabilId periodoAbertura = PeriodoContabilId.novo();
    private final LocalDate dataAbertura = LocalDate.of(2027, 1, 1);
    private final Clock relogio = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final ContaContabilId contaResultadoExercicio = ContaContabilId.novo();
    private final ContaContabilId contaResultadoAcumulado = ContaContabilId.novo();
    private final ParametroTransposicaoAbertura parametro =
            new ParametroTransposicaoAbertura(contaResultadoExercicio, contaResultadoAcumulado);

    @BeforeEach
    void setUp() {
        abertura = new TransporSaldosAbertura(repositorio, contadorFato, consultaSaldo, periodoContabil, auditoria, relogio);
    }

    private Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("saldo credor (superávit): debita a origem e credita o destino pelo valor absoluto")
    void transpoeSaldoCredor() {
        Sessao sessao = sessao();
        when(consultaSaldo.saldoDevedorLiquido(enteId, contaResultadoExercicio)).thenReturn(Dinheiro.de("-1000.00"));
        when(periodoContabil.periodoAbertoPara(enteId, dataAbertura)).thenReturn(periodoAbertura);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        List<FatoContabil> fatos = abertura.executar(sessao, enteId, 2026, List.of(parametro));

        assertThat(fatos).hasSize(1);
        FatoContabil fato = fatos.get(0);
        assertThat(fato.tipoEvento()).isEqualTo(TipoEvento.ABERTURA);
        assertThat(fato.enteId()).isEqualTo(enteId);
        assertThat(fato.periodoId()).isEqualTo(periodoAbertura);
        assertThat(fato.dataCompetencia()).isEqualTo(dataAbertura);
        assertThat(fato.lancamentos()).hasSize(2);

        var lancamentoOrigem = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaResultadoExercicio)).findFirst().orElseThrow();
        var lancamentoDestino = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaResultadoAcumulado)).findFirst().orElseThrow();

        assertThat(lancamentoOrigem.natureza()).isEqualTo(Natureza.DEBITO);
        assertThat(lancamentoOrigem.valor()).isEqualTo(Dinheiro.de("1000.00"));
        assertThat(lancamentoDestino.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(lancamentoDestino.valor()).isEqualTo(Dinheiro.de("1000.00"));

        verify(repositorio).inserir(fato);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("razao_abertura_exercicio_transposicao");
        assertThat(evento.getValue().detalhes())
                .containsEntry("exercicio", "2027")
                .containsEntry("valor", "1000.00")
                .containsEntry("contaOrigem", contaResultadoExercicio.valor().toString())
                .containsEntry("contaDestino", contaResultadoAcumulado.valor().toString());
    }

    @Test
    @DisplayName("saldo devedor (déficit): credita a origem e debita o destino pelo valor absoluto")
    void transpoeSaldoDevedor() {
        Sessao sessao = sessao();
        when(consultaSaldo.saldoDevedorLiquido(enteId, contaResultadoExercicio)).thenReturn(Dinheiro.de("500.00"));
        when(periodoContabil.periodoAbertoPara(enteId, dataAbertura)).thenReturn(periodoAbertura);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        List<FatoContabil> fatos = abertura.executar(sessao, enteId, 2026, List.of(parametro));

        assertThat(fatos).hasSize(1);
        var lancamentoOrigem = fatos.get(0).lancamentos().stream()
                .filter(l -> l.contaId().equals(contaResultadoExercicio)).findFirst().orElseThrow();
        var lancamentoDestino = fatos.get(0).lancamentos().stream()
                .filter(l -> l.contaId().equals(contaResultadoAcumulado)).findFirst().orElseThrow();

        assertThat(lancamentoOrigem.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(lancamentoOrigem.valor()).isEqualTo(Dinheiro.de("500.00"));
        assertThat(lancamentoDestino.natureza()).isEqualTo(Natureza.DEBITO);
        assertThat(lancamentoDestino.valor()).isEqualTo(Dinheiro.de("500.00"));
    }

    @Test
    @DisplayName("saldo zero não gera fato nem consulta o período de abertura")
    void ignoraSaldoZero() {
        Sessao sessao = sessao();
        when(consultaSaldo.saldoDevedorLiquido(enteId, contaResultadoExercicio)).thenReturn(Dinheiro.zero());

        List<FatoContabil> fatos = abertura.executar(sessao, enteId, 2026, List.of(parametro));

        assertThat(fatos).isEmpty();
        verify(repositorio, never()).inserir(any());
        verifyNoInteractions(periodoContabil, contadorFato);
        verify(auditoria, never()).append(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("dois parâmetros geram dois fatos de abertura independentes")
    void transpoeMultiplosParametros() {
        Sessao sessao = sessao();
        ContaContabilId outraOrigem = ContaContabilId.novo();
        ContaContabilId outroDestino = ContaContabilId.novo();
        ParametroTransposicaoAbertura outroParametro = new ParametroTransposicaoAbertura(outraOrigem, outroDestino);

        when(consultaSaldo.saldoDevedorLiquido(enteId, contaResultadoExercicio)).thenReturn(Dinheiro.de("-1000.00"));
        when(consultaSaldo.saldoDevedorLiquido(enteId, outraOrigem)).thenReturn(Dinheiro.de("-200.00"));
        when(periodoContabil.periodoAbertoPara(enteId, dataAbertura)).thenReturn(periodoAbertura);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L, 2L);

        List<FatoContabil> fatos = abertura.executar(sessao, enteId, 2026, List.of(parametro, outroParametro));

        assertThat(fatos).hasSize(2);
        assertThat(fatos).allMatch(f -> f.tipoEvento() == TipoEvento.ABERTURA);
    }

    @Test
    @DisplayName("lista vazia de parâmetros não gera fatos nem consulta portas")
    void semParametrosNaoGeraFatos() {
        Sessao sessao = sessao();

        List<FatoContabil> fatos = abertura.executar(sessao, enteId, 2026, List.of());

        assertThat(fatos).isEmpty();
        verifyNoInteractions(consultaSaldo, periodoContabil, contadorFato, repositorio, auditoria);
    }
}
