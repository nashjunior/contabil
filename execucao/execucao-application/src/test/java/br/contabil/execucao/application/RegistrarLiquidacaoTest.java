package br.contabil.execucao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.SaldoEmpenho;
import br.contabil.execucao.domain.SaldoInsuficienteException;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort.SolicitacaoEscrituracaoLiquidacao;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
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

@ExtendWith(MockitoExtension.class)
class RegistrarLiquidacaoTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private SaldosExecucaoPort saldos;

    @Mock
    private ExecucaoContabilPort escrituracao;

    @Mock
    private LiquidacaoRepository repositorio;

    @Mock
    private PublicacaoTransparenciaExecucaoPort publicacaoTransparencia;

    @Mock
    private AuditoriaEscrita auditoria;

    private RegistrarLiquidacao useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final EmpenhoId empenhoId = EmpenhoId.novo();
    private final LocalDate dataCompetencia = LocalDate.of(2026, 7, 15);
    private final DocumentoSuporte notaFiscal = DocumentoSuporte.de("NF", "123", LocalDate.of(2026, 7, 10));
    private static final Recurso RECURSO = new Recurso("execucao:liquidacao");

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
        useCase = new RegistrarLiquidacao(
                new ControleAcesso(servicoIdentidade),
                saldos,
                escrituracao,
                repositorio,
                publicacaoTransparencia,
                auditoria,
                relogioFixo);
    }

    @Test
    @DisplayName("consulta saldo, escritura fato contábil e persiste liquidação")
    void registraComSucesso() {
        Sessao sessao = sessao(true);
        ReferenciaFatoContabil fatoContabilId = new ReferenciaFatoContabil(UUID.randomUUID());
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(saldos.saldoEmpenho(enteId, empenhoId))
                .thenReturn(new SaldoEmpenho(empenhoId, Dinheiro.de("1000.00"), Dinheiro.de("200.00")));
        when(escrituracao.registrarLiquidacao(any(SolicitacaoEscrituracaoLiquidacao.class)))
                .thenReturn(fatoContabilId);

        Liquidacao liquidacao = useCase.executar(
                sessao,
                enteId,
                empenhoId,
                dataCompetencia,
                Dinheiro.de("300.00"),
                List.of(notaFiscal),
                "liquidação NF 123");

        assertThat(liquidacao.empenhoId()).isEqualTo(empenhoId);
        assertThat(liquidacao.fatoContabilId()).isEqualTo(fatoContabilId.valor());
        verify(repositorio).inserir(liquidacao);
        verify(publicacaoTransparencia).publicar(liquidacao, sessao);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_liquidacao_registrada");
        assertThat(evento.getValue().detalhes())
                .containsEntry("empenhoId", empenhoId.valor().toString())
                .containsEntry("fatoContabilId", fatoContabilId.valor().toString())
                .containsEntry("valor", "300.00");
    }

    @Test
    @DisplayName("documento ausente falha antes de consultar saldos ou escriturar")
    void rejeitaDocumentoAusenteAntesDeIo() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        empenhoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        List.of(),
                        "liquidação sem documento"))
                .isInstanceOf(ExecucaoInvalidaException.class);

        verifyNoInteractions(saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
    }

    @Test
    @DisplayName("saldo insuficiente falha antes de escriturar e persistir")
    void rejeitaSemSaldoAntesDeEscriturar() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(saldos.saldoEmpenho(enteId, empenhoId))
                .thenReturn(new SaldoEmpenho(empenhoId, Dinheiro.de("1000.00"), Dinheiro.de("800.00")));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        empenhoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        List.of(notaFiscal),
                        "liquidação acima do saldo"))
                .isInstanceOf(SaldoInsuficienteException.class);

        verify(escrituracao, never()).registrarLiquidacao(any());
        verify(repositorio, never()).inserir(any());
        verify(publicacaoTransparencia, never()).publicar(any(Liquidacao.class), any(Sessao.class));
        verify(auditoria, never()).append(any());
    }

    @Test
    @DisplayName("falha da escrituração contábil não persiste liquidação parcial")
    void falhaEscrituracaoNaoPersisteLiquidacao() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(saldos.saldoEmpenho(enteId, empenhoId))
                .thenReturn(new SaldoEmpenho(empenhoId, Dinheiro.de("1000.00"), Dinheiro.de("200.00")));
        when(escrituracao.registrarLiquidacao(any(SolicitacaoEscrituracaoLiquidacao.class)))
                .thenThrow(new IllegalStateException("razão indisponível"));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
                        enteId,
                        empenhoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        List.of(notaFiscal),
                        "liquidação NF 123"))
                .isInstanceOf(IllegalStateException.class);

        verify(repositorio, never()).inserir(any());
        verify(publicacaoTransparencia, never()).publicar(any(Liquidacao.class), any(Sessao.class));
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
                        empenhoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        List.of(notaFiscal),
                        "liquidação NF 123"))
                .isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
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
                        empenhoId,
                        dataCompetencia,
                        Dinheiro.de("300.00"),
                        List.of(notaFiscal),
                        "liquidação NF 123"))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria);
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
}
