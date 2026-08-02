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
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;

@ExtendWith(MockitoExtension.class)
class EncerrarContasOrcamentariasTest {

    @Mock
    private FatoContabilRepository repositorio;

    @Mock
    private ContadorFatoPort contadorFato;

    @Mock
    private ConsultaSaldoPort consultaSaldo;

    @Mock
    private AuditoriaEscrita auditoria;

    private EncerrarContasOrcamentarias encerramento;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final PeriodoContabilId periodoId = PeriodoContabilId.novo();
    private final LocalDate dataEncerramento = LocalDate.of(2026, 12, 31);
    private final Clock relogio = Clock.fixed(Instant.parse("2026-12-31T23:00:00Z"), ZoneOffset.UTC);

    private final ContaContabilId contaCreditoDisponivel = ContaContabilId.novo();
    private final ContaContabilId contaCreditoInicial = ContaContabilId.novo();

    private final ParametroEncerramentoOrcamentario parametroCreditoDisponivel = new ParametroEncerramentoOrcamentario(
            contaCreditoDisponivel, contaCreditoInicial, Natureza.DEBITO);

    @BeforeEach
    void setUp() {
        encerramento = new EncerrarContasOrcamentarias(repositorio, contadorFato, consultaSaldo, auditoria, relogio);
    }

    private Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("zera a conta de controle contra a contrapartida num único fato Σ=Σ")
    void geraFatoDeEncerramentoZerandoAContaDeControle() {
        Sessao sessao = sessao();
        when(consultaSaldo.saldoDevedorLiquido(enteId, contaCreditoDisponivel)).thenReturn(Dinheiro.de("1500.00"));
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        Optional<FatoContabil> resultado = encerramento.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, List.of(parametroCreditoDisponivel));

        assertThat(resultado).isPresent();
        FatoContabil fato = resultado.get();
        assertThat(fato.tipoEvento()).isEqualTo(TipoEvento.ENCERRAMENTO);
        assertThat(fato.enteId()).isEqualTo(enteId);
        assertThat(fato.periodoId()).isEqualTo(periodoId);
        assertThat(fato.dataCompetencia()).isEqualTo(dataEncerramento);
        assertThat(fato.lancamentos()).hasSize(2);

        var lancamentoControle = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaCreditoDisponivel)).findFirst().orElseThrow();
        var lancamentoContrapartida = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaCreditoInicial)).findFirst().orElseThrow();

        assertThat(lancamentoControle.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(lancamentoContrapartida.natureza()).isEqualTo(Natureza.DEBITO);
        assertThat(lancamentoControle.valor()).isEqualTo(Dinheiro.de("1500.00"));
        assertThat(lancamentoContrapartida.valor()).isEqualTo(Dinheiro.de("1500.00"));

        verify(repositorio).inserir(fato);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("razao_encerramento_contas_orcamentarias");
        assertThat(evento.getValue().detalhes())
                .containsEntry("exercicio", "2026")
                .containsEntry("contasEncerradas", "1");
    }

    @Test
    @DisplayName("contas já zeradas são ignoradas e não entram no fato")
    void ignoraContasComSaldoZero() {
        Sessao sessao = sessao();
        when(consultaSaldo.saldoDevedorLiquido(enteId, contaCreditoDisponivel)).thenReturn(Dinheiro.zero());

        Optional<FatoContabil> resultado = encerramento.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, List.of(parametroCreditoDisponivel));

        assertThat(resultado).isEmpty();
        verify(repositorio, never()).inserir(any());
        verify(auditoria, never()).append(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("múltiplas contas fecham no MESMO fato — não é um fato por conta")
    void multiplasContasFechamNoMesmoFatoESemContaResidual() {
        Sessao sessao = sessao();
        ContaContabilId contaEmpenhadoALiquidar = ContaContabilId.novo();
        ParametroEncerramentoOrcamentario parametroEmpenhadoALiquidar = new ParametroEncerramentoOrcamentario(
                contaEmpenhadoALiquidar, contaCreditoInicial, Natureza.DEBITO);

        when(consultaSaldo.saldoDevedorLiquido(enteId, contaCreditoDisponivel)).thenReturn(Dinheiro.de("1500.00"));
        when(consultaSaldo.saldoDevedorLiquido(enteId, contaEmpenhadoALiquidar)).thenReturn(Dinheiro.de("300.00"));
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        Optional<FatoContabil> resultado = encerramento.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento,
                List.of(parametroCreditoDisponivel, parametroEmpenhadoALiquidar));

        assertThat(resultado).isPresent();
        FatoContabil fato = resultado.get();
        // 2 pares = 4 lançamentos, todos no MESMO fato — nenhuma conta de "resultado
        // orçamentário" residual, cada par transfere exatamente o mesmo valor entre si.
        assertThat(fato.lancamentos()).hasSize(4);
        verify(repositorio).inserir(fato);
        verify(contadorFato).proximoNumeroSeq(enteId);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().detalhes()).containsEntry("contasEncerradas", "2");
    }

    @Test
    @DisplayName("conta de controle naturalmente credora (saldo negativo, ex.: execução da receita) "
            + "zera corretamente — não lança sobre o sinal bruto de Σdébito-Σcrédito")
    void zeraContaDeControleComSaldoNegativoNaturezaCredora() {
        Sessao sessao = sessao();
        ContaContabilId contaReceitaRealizada = ContaContabilId.novo();
        ContaContabilId contaPrevisaoInicial = ContaContabilId.novo();
        // Execução da receita (6.2.1.x) é naturalmente credora: saldoDevedorLiquido
        // (Σdébito-Σcrédito) vem NEGATIVO — a contrapartida (Previsão Inicial, 5.2.1.1.1)
        // recebe CREDITO ao encerrar.
        ParametroEncerramentoOrcamentario parametroReceita = new ParametroEncerramentoOrcamentario(
                contaReceitaRealizada, contaPrevisaoInicial, Natureza.CREDITO);

        when(consultaSaldo.saldoDevedorLiquido(enteId, contaReceitaRealizada)).thenReturn(Dinheiro.de("-2000.00"));
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        Optional<FatoContabil> resultado = encerramento.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, List.of(parametroReceita));

        assertThat(resultado).isPresent();
        FatoContabil fato = resultado.get();
        assertThat(fato.lancamentos()).hasSize(2);

        var lancamentoControle = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaReceitaRealizada)).findFirst().orElseThrow();
        var lancamentoContrapartida = fato.lancamentos().stream()
                .filter(l -> l.contaId().equals(contaPrevisaoInicial)).findFirst().orElseThrow();

        assertThat(lancamentoControle.natureza()).isEqualTo(Natureza.DEBITO);
        assertThat(lancamentoControle.valor()).isEqualTo(Dinheiro.de("2000.00"));
        assertThat(lancamentoContrapartida.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(lancamentoContrapartida.valor()).isEqualTo(Dinheiro.de("2000.00"));

        verify(repositorio).inserir(fato);
    }

    @Test
    @DisplayName("lista vazia de parâmetros não gera fato nem consulta o port")
    void semParametrosNaoGeraFato() {
        Sessao sessao = sessao();

        Optional<FatoContabil> resultado = encerramento.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, List.of());

        assertThat(resultado).isEmpty();
        verify(consultaSaldo, never()).saldoDevedorLiquido(any(), any());
        verify(repositorio, never()).inserir(any());
    }
}
