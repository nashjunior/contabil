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
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.LinhaBalancete;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.BalancetePort;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;

@ExtendWith(MockitoExtension.class)
class ApurarResultadoPatrimonialTest {

    @Mock
    private FatoContabilRepository repositorio;

    @Mock
    private ContadorFatoPort contadorFato;

    @Mock
    private BalancetePort balancetePort;

    @Mock
    private AuditoriaEscrita auditoria;

    private ApurarResultadoPatrimonial apuracao;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final PeriodoContabilId periodoId = PeriodoContabilId.novo();
    private final LocalDate dataEncerramento = LocalDate.of(2026, 12, 31);
    private final Clock relogio = Clock.fixed(Instant.parse("2026-12-31T23:00:00Z"), ZoneOffset.UTC);
    private final ContaContabilId contaResultado = ContaContabilId.novo();

    @BeforeEach
    void setUp() {
        apuracao = new ApurarResultadoPatrimonial(repositorio, contadorFato, balancetePort, auditoria, relogio);
    }

    private Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    private static LinhaBalancete linha(ContaContabilId contaId, String codigo, String naturezaSaldo, Dinheiro saldoAtual) {
        return new LinhaBalancete(
                contaId, codigo, "conta " + codigo, naturezaSaldo, saldoAtual, Dinheiro.zero(), Dinheiro.zero(), saldoAtual);
    }

    @Test
    @DisplayName("zera VPD (classe 3) e VPA (classe 4) num único fato Σdébito=Σcrédito contra a conta de resultado")
    void zeraVpdEVpaNumUnicoFatoBalanceado() {
        Sessao sessao = sessao();
        ContaContabilId contaVpd = ContaContabilId.novo();
        ContaContabilId contaVpa = ContaContabilId.novo();

        Balancete balancete = new Balancete(enteId, 2026, 13, List.of(
                linha(contaVpd, "3.1.1.1.01.01", "D", Dinheiro.de("300.00")),
                linha(contaVpa, "4.1.1.1.01.01", "C", Dinheiro.de("-200.00"))));
        when(balancetePort.balancete(enteId, 2026, 13)).thenReturn(balancete);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        Optional<FatoContabil> resultado = apuracao.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, contaResultado);

        assertThat(resultado).isPresent();
        FatoContabil fato = resultado.get();
        assertThat(fato.tipoEvento()).isEqualTo(TipoEvento.ENCERRAMENTO);
        assertThat(fato.periodoId()).isEqualTo(periodoId);
        assertThat(fato.dataCompetencia()).isEqualTo(dataEncerramento);
        assertThat(fato.lancamentos()).hasSize(4);

        Dinheiro somaDebito = fato.lancamentos().stream()
                .filter(l -> l.natureza() == Natureza.DEBITO)
                .map(Lancamento::valor)
                .reduce(Dinheiro.zero(), Dinheiro::somar);
        Dinheiro somaCredito = fato.lancamentos().stream()
                .filter(l -> l.natureza() == Natureza.CREDITO)
                .map(Lancamento::valor)
                .reduce(Dinheiro.zero(), Dinheiro::somar);
        assertThat(somaDebito).isEqualTo(somaCredito);

        Lancamento lancamentoVpd = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaVpd)).findFirst().orElseThrow();
        assertThat(lancamentoVpd.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(lancamentoVpd.valor()).isEqualTo(Dinheiro.de("300.00"));

        Lancamento lancamentoVpa = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaVpa)).findFirst().orElseThrow();
        assertThat(lancamentoVpa.natureza()).isEqualTo(Natureza.DEBITO);
        assertThat(lancamentoVpa.valor()).isEqualTo(Dinheiro.de("200.00"));

        List<Lancamento> lancamentosResultado = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaResultado)).toList();
        assertThat(lancamentosResultado).hasSize(2);
        assertThat(lancamentosResultado)
                .anySatisfy(l -> {
                    assertThat(l.natureza()).isEqualTo(Natureza.DEBITO);
                    assertThat(l.valor()).isEqualTo(Dinheiro.de("300.00"));
                })
                .anySatisfy(l -> {
                    assertThat(l.natureza()).isEqualTo(Natureza.CREDITO);
                    assertThat(l.valor()).isEqualTo(Dinheiro.de("200.00"));
                });

        verify(repositorio).inserir(fato);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("razao_apuracao_resultado_patrimonial");
        assertThat(evento.getValue().detalhes())
                .containsEntry("exercicio", "2026")
                .containsEntry("totalContrapartidaDebito", "300.00")
                .containsEntry("totalContrapartidaCredito", "200.00");
    }

    @Test
    @DisplayName("ignora contas fora das classes 3/4 (ex.: ativo, classe 1)")
    void ignoraContasForaDeVpdVpa() {
        Sessao sessao = sessao();
        ContaContabilId contaAtivo = ContaContabilId.novo();
        ContaContabilId contaVpd = ContaContabilId.novo();

        Balancete balancete = new Balancete(enteId, 2026, 13, List.of(
                linha(contaAtivo, "1.1.1.01.01", "D", Dinheiro.de("1000.00")),
                linha(contaVpd, "3.1.1.1.01.01", "D", Dinheiro.de("50.00"))));
        when(balancetePort.balancete(enteId, 2026, 13)).thenReturn(balancete);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        Optional<FatoContabil> resultado = apuracao.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, contaResultado);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().lancamentos())
                .noneMatch(l -> l.contaId().equals(contaAtivo));
    }

    @Test
    @DisplayName("sem saldo de VPD/VPA no mês 13 não gera fato")
    void semSaldoNaoGeraFato() {
        Sessao sessao = sessao();
        Balancete balancete = new Balancete(enteId, 2026, 13, List.of());
        when(balancetePort.balancete(enteId, 2026, 13)).thenReturn(balancete);

        Optional<FatoContabil> resultado = apuracao.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, contaResultado);

        assertThat(resultado).isEmpty();
        verify(repositorio, never()).inserir(any());
        verify(auditoria, never()).append(any(EventoAuditoria.class));
        verify(contadorFato, never()).proximoNumeroSeq(any());
    }

    @Test
    @DisplayName("usa o sinal real do saldo, não a natureza natural — VPD anômala (saldo credor) zera via débito")
    void usaSinalRealDoSaldoParaContaAnomala() {
        Sessao sessao = sessao();
        ContaContabilId contaVpdAnomala = ContaContabilId.novo();

        // Classe 3 (VPD), naturezaSaldo natural "D", mas saldo atual credor (negativo) —
        // anomalia; o zeramento tem que ser por DÉBITO (zera o saldo real), não por
        // CRÉDITO (que dobraria o saldo credor em vez de zerá-lo).
        Balancete balancete = new Balancete(enteId, 2026, 13, List.of(
                linha(contaVpdAnomala, "3.1.1.1.01.01", "D", Dinheiro.de("-40.00"))));
        when(balancetePort.balancete(enteId, 2026, 13)).thenReturn(balancete);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        Optional<FatoContabil> resultado = apuracao.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, contaResultado);

        assertThat(resultado).isPresent();
        Lancamento lancamentoAnomalo = resultado.get().lancamentos().stream()
                .filter(l -> l.contaId().equals(contaVpdAnomala)).findFirst().orElseThrow();
        assertThat(lancamentoAnomalo.natureza()).isEqualTo(Natureza.DEBITO);
        assertThat(lancamentoAnomalo.valor()).isEqualTo(Dinheiro.de("40.00"));
    }
}
