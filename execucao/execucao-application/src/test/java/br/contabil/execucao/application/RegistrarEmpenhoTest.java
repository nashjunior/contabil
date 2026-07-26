package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.SaldoDotacao;
import br.contabil.execucao.domain.SaldoInsuficienteException;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.repository.ContadorEmpenhoPort;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort.SolicitacaoEscrituracaoEmpenho;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrarEmpenhoTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private SaldosExecucaoPort saldos;

    @Mock
    private ExecucaoContabilPort escrituracao;

    @Mock
    private ContadorEmpenhoPort contadorEmpenho;

    @Mock
    private EmpenhoRepository repositorio;

    @Mock
    private PublicacaoTransparenciaExecucaoPort publicacaoTransparencia;

    @Mock
    private AuditoriaEscrita auditoria;

    private RegistrarEmpenho useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final DotacaoId dotacaoId = DotacaoId.novo();
    private final UUID credorId = UUID.randomUUID();
    private final UUID unidadeGestoraId = UUID.randomUUID();
    private final LocalDate dataFato = LocalDate.of(2026, 7, 20);
    private static final Recurso RECURSO = new Recurso("execucao:empenho");

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
        useCase = new RegistrarEmpenho(
                new ControleAcesso(servicoIdentidade),
                saldos,
                escrituracao,
                contadorEmpenho,
                repositorio,
                publicacaoTransparencia,
                auditoria,
                relogioFixo);
    }

    private Sessao sessao(boolean mfaConcluido) {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                enteId,
                Optional.empty(),
                mfaConcluido,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("consulta saldo da dotação, escritura fato contábil e persiste empenho")
    void registraComSucesso() {
        Sessao sessao = sessao(true);
        UUID fatoContabilId = UUID.randomUUID();
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(saldos.saldoDotacao(enteId, dotacaoId))
                .thenReturn(new SaldoDotacao(dotacaoId, Dinheiro.de("5000.00"), Dinheiro.de("1000.00")));
        when(escrituracao.registrarEmpenho(any(SolicitacaoEscrituracaoEmpenho.class))).thenReturn(fatoContabilId);
        when(contadorEmpenho.proximoNumero(enteId, 2026)).thenReturn(7L);

        Empenho empenho = useCase.executar(
                sessao,
                enteId,
                dotacaoId,
                TipoEmpenho.ORDINARIO,
                credorId,
                unidadeGestoraId,
                null,
                Dinheiro.de("1500.00"),
                dataFato,
                2026,
                "04.122.0001.2001",
                "0100000000",
                "empenho de material de expediente");

        assertThat(empenho.dotacaoId()).isEqualTo(dotacaoId);
        assertThat(empenho.numeroSequencial()).isEqualTo(7L);
        assertThat(empenho.fatoContabilId()).isEqualTo(fatoContabilId);
        verify(repositorio).inserir(empenho);
        verify(publicacaoTransparencia).publicar(empenho, sessao);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_empenho_registrado");
        assertThat(evento.getValue().detalhes())
                .containsEntry("dotacaoId", dotacaoId.valor().toString())
                .containsEntry("fatoContabilId", fatoContabilId.toString())
                .containsEntry("valor", "1500.00");
    }

    @Test
    @DisplayName("valor inválido falha antes de consultar saldo, escriturar ou persistir")
    void rejeitaValorInvalidoAntesDeIo() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        dotacaoId,
                        TipoEmpenho.ORDINARIO,
                        credorId,
                        unidadeGestoraId,
                        null,
                        Dinheiro.zero(),
                        dataFato,
                        2026,
                        "04.122.0001.2001",
                        "0100000000",
                        "empenho inválido"))
                .isInstanceOf(ExecucaoInvalidaException.class);

        verifyNoInteractions(saldos, escrituracao, contadorEmpenho, repositorio, publicacaoTransparencia, auditoria);
    }

    @Test
    @DisplayName("crédito insuficiente da dotação falha antes de escriturar e persistir")
    void rejeitaSemSaldoAntesDeEscriturar() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(saldos.saldoDotacao(enteId, dotacaoId))
                .thenReturn(new SaldoDotacao(dotacaoId, Dinheiro.de("1000.00"), Dinheiro.de("900.00")));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        dotacaoId,
                        TipoEmpenho.ORDINARIO,
                        credorId,
                        unidadeGestoraId,
                        null,
                        Dinheiro.de("300.00"),
                        dataFato,
                        2026,
                        "04.122.0001.2001",
                        "0100000000",
                        "empenho acima do saldo"))
                .isInstanceOf(SaldoInsuficienteException.class);

        verify(escrituracao, never()).registrarEmpenho(any());
        verify(contadorEmpenho, never()).proximoNumero(any(), anyInt());
        verify(repositorio, never()).inserir(any());
        verify(publicacaoTransparencia, never()).publicar(any(Empenho.class), any(Sessao.class));
        verify(auditoria, never()).append(any());
    }

    @Test
    @DisplayName("falha da escrituração contábil não persiste empenho parcial")
    void falhaEscrituracaoNaoPersisteEmpenho() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(saldos.saldoDotacao(enteId, dotacaoId))
                .thenReturn(new SaldoDotacao(dotacaoId, Dinheiro.de("5000.00"), Dinheiro.de("1000.00")));
        when(escrituracao.registrarEmpenho(any(SolicitacaoEscrituracaoEmpenho.class)))
                .thenThrow(new IllegalStateException("razão indisponível"));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        dotacaoId,
                        TipoEmpenho.ORDINARIO,
                        credorId,
                        unidadeGestoraId,
                        null,
                        Dinheiro.de("300.00"),
                        dataFato,
                        2026,
                        "04.122.0001.2001",
                        "0100000000",
                        "empenho falho"))
                .isInstanceOf(IllegalStateException.class);

        verify(contadorEmpenho, never()).proximoNumero(any(), anyInt());
        verify(repositorio, never()).inserir(any());
        verify(publicacaoTransparencia, never()).publicar(any(Empenho.class), any(Sessao.class));
        verify(auditoria, never()).append(any());
    }

    @Test
    @DisplayName("RBAC/MFA bloqueia antes de qualquer consulta de execução")
    void negaSemMfaAntesDeIo() {
        Sessao sessao = sessao(false);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        dotacaoId,
                        TipoEmpenho.ORDINARIO,
                        credorId,
                        unidadeGestoraId,
                        null,
                        Dinheiro.de("300.00"),
                        dataFato,
                        2026,
                        "04.122.0001.2001",
                        "0100000000",
                        "empenho"))
                .isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(saldos, escrituracao, contadorEmpenho, repositorio, publicacaoTransparencia, auditoria);
    }

    @Test
    @DisplayName("tenant divergente falha antes do RBAC e dos ports")
    void negaTenantDivergente() {
        Sessao sessaoOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(
                        sessaoOutroEnte,
                        enteId,
                        dotacaoId,
                        TipoEmpenho.ORDINARIO,
                        credorId,
                        unidadeGestoraId,
                        null,
                        Dinheiro.de("300.00"),
                        dataFato,
                        2026,
                        "04.122.0001.2001",
                        "0100000000",
                        "empenho"))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(saldos, escrituracao, contadorEmpenho, repositorio, publicacaoTransparencia, auditoria);
    }
}
