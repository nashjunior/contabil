package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.EncerramentoConflitanteException;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabil;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.PeriodoContabilNaoEncontradoException;
import br.contabil.razao.domain.StatusPeriodo;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

@ExtendWith(MockitoExtension.class)
class EncerrarExercicioTest {

    private static final ServicoIdentidade.Recurso RECURSO_PERIODO =
            new ServicoIdentidade.Recurso("razao:periodo_contabil");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private PeriodoContabilRepository periodoRepositorio;

    @Mock
    private ApurarResultadoPatrimonial apurarResultadoPatrimonial;

    @Mock
    private InscreverRestosAPagar inscreverRP;

    @Mock
    private EncerrarContasOrcamentarias encerrarContasOrcamentarias;

    @Mock
    private EncerrarDdrPorFonte encerrarDdr;

    @Mock
    private TransporSaldosAbertura transporSaldosAbertura;

    @Mock
    private TransporDdrPorFonteAbertura transporDdrAbertura;

    @Mock
    private AuditoriaEscrita auditoria;

    private EncerrarExercicio useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final Clock relogio = Clock.fixed(Instant.parse("2026-12-31T23:00:00Z"), ZoneOffset.UTC);

    private Sessao sessaoComMfa() {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                enteId,
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                enteId,
                Optional.empty(),
                false,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new EncerrarExercicio(
                new ControleAcesso(servicoIdentidade),
                periodoRepositorio,
                apurarResultadoPatrimonial,
                Optional.empty(),
                inscreverRP,
                ente -> List.of(),
                encerrarContasOrcamentarias,
                List.of(),
                encerrarDdr,
                ente -> List.of(),
                transporSaldosAbertura,
                List.of(),
                transporDdrAbertura,
                ente -> List.of(),
                auditoria,
                relogio);
    }

    @Test
    @DisplayName("mês 13 aberto: inscreve RP, encerra as contas orçamentárias, encerra a DDR por fonte, "
            + "encerra o período, abre o exercício seguinte e audita o exercício")
    void encerraMes13ComSucesso() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 13, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodoExercicio));
        when(periodoRepositorio.encerrar(any())).thenReturn(true);

        PeriodoContabil resultado = useCase.executar(sessao, enteId, 2026);

        assertThat(resultado.status()).isEqualTo(StatusPeriodo.ENCERRADO);
        assertThat(resultado.exercicio()).isEqualTo(2026);
        assertThat(resultado.mes()).isEqualTo(13);

        InOrder ordem = inOrder(
                inscreverRP, encerrarContasOrcamentarias, encerrarDdr, periodoRepositorio, transporSaldosAbertura,
                transporDdrAbertura);
        ordem.verify(inscreverRP)
                .executar(eq(sessao), eq(enteId), eq(2026), eq(periodoExercicio.id()), any(), eq(List.of()));
        ordem.verify(encerrarContasOrcamentarias)
                .executar(eq(sessao), eq(enteId), eq(2026), eq(periodoExercicio.id()), any(), eq(List.of()));
        ordem.verify(encerrarDdr)
                .executar(eq(sessao), eq(enteId), eq(2026), eq(periodoExercicio.id()), any(), eq(List.of()));
        ordem.verify(periodoRepositorio).encerrar(any());
        ordem.verify(transporSaldosAbertura).executar(eq(sessao), eq(enteId), eq(2026), eq(List.of()));
        ordem.verify(transporDdrAbertura).executar(eq(sessao), eq(enteId), eq(2026), eq(List.of()));

        verify(apurarResultadoPatrimonial, never()).executar(any(), any(), anyInt(), any(), any(), any());

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("razao_exercicio_encerrado");
        assertThat(evento.getValue().detalhes()).containsEntry("exercicio", "2026");
    }

    @Test
    @DisplayName("RAZ-257: com contaResultadoApurado configurada, apura o resultado patrimonial antes da inscrição de RP")
    void apuraResultadoPatrimonialQuandoContaConfigurada() {
        ContaContabilId contaResultado = ContaContabilId.novo();
        EncerrarExercicio useCaseComApuracao = new EncerrarExercicio(
                new ControleAcesso(servicoIdentidade),
                periodoRepositorio,
                apurarResultadoPatrimonial,
                Optional.of(contaResultado),
                inscreverRP,
                ente -> List.of(),
                encerrarContasOrcamentarias,
                List.of(),
                encerrarDdr,
                ente -> List.of(),
                transporSaldosAbertura,
                List.of(),
                transporDdrAbertura,
                ente -> List.of(),
                auditoria,
                relogio);

        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 13, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodoExercicio));
        when(periodoRepositorio.encerrar(any())).thenReturn(true);

        useCaseComApuracao.executar(sessao, enteId, 2026);

        InOrder ordem = inOrder(apurarResultadoPatrimonial, inscreverRP);
        ordem.verify(apurarResultadoPatrimonial)
                .executar(eq(sessao), eq(enteId), eq(2026), eq(periodoExercicio.id()), any(), eq(contaResultado));
        ordem.verify(inscreverRP)
                .executar(eq(sessao), eq(enteId), eq(2026), eq(periodoExercicio.id()), any(), eq(List.of()));
    }

    @Test
    @DisplayName("RAZ-260: resolve parâmetros de RP do ente e repassa lista real ao colaborador")
    void repassaParametrosResolvidosPorEnteParaInscricaoRp() {
        List<ParametroInscricaoRP> parametros = List.of(new ParametroInscricaoRP(
                ContaContabilId.novo(), ContaContabilId.novo(), Natureza.CREDITO));
        EncerrarExercicio useCaseComParametrosRp = new EncerrarExercicio(
                new ControleAcesso(servicoIdentidade),
                periodoRepositorio,
                apurarResultadoPatrimonial,
                Optional.empty(),
                inscreverRP,
                ente -> {
                    assertThat(ente).isEqualTo(enteId);
                    return parametros;
                },
                encerrarContasOrcamentarias,
                List.of(),
                encerrarDdr,
                ente -> List.of(),
                transporSaldosAbertura,
                List.of(),
                transporDdrAbertura,
                ente -> List.of(),
                auditoria,
                relogio);

        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 13, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodoExercicio));
        when(periodoRepositorio.encerrar(any())).thenReturn(true);

        useCaseComParametrosRp.executar(sessao, enteId, 2026);

        verify(inscreverRP)
                .executar(eq(sessao), eq(enteId), eq(2026), eq(periodoExercicio.id()), any(), eq(parametros));
    }

    @Test
    @DisplayName("RAZ-266: resolve parâmetros de DDR (encerramento e abertura) do ente e repassa listas reais")
    void repassaParametrosDdrResolvidosPorEnteParaEncerramentoEAbertura() {
        List<ParametroEncerramentoDdr> parametrosDdr = List.of(new ParametroEncerramentoDdr(
                ContaContabilId.novo(), ContaContabilId.novo(), Natureza.CREDITO));
        List<ParametroTransposicaoDdrAbertura> parametrosAberturaDdr = List.of(new ParametroTransposicaoDdrAbertura(
                ContaContabilId.novo(), ContaContabilId.novo(), Natureza.CREDITO));

        EncerrarExercicio useCaseComParametrosDdr = new EncerrarExercicio(
                new ControleAcesso(servicoIdentidade),
                periodoRepositorio,
                apurarResultadoPatrimonial,
                Optional.empty(),
                inscreverRP,
                ente -> List.of(),
                encerrarContasOrcamentarias,
                List.of(),
                encerrarDdr,
                ente -> {
                    assertThat(ente).isEqualTo(enteId);
                    return parametrosDdr;
                },
                transporSaldosAbertura,
                List.of(),
                transporDdrAbertura,
                ente -> {
                    assertThat(ente).isEqualTo(enteId);
                    return parametrosAberturaDdr;
                },
                auditoria,
                relogio);

        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 13, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodoExercicio));
        when(periodoRepositorio.encerrar(any())).thenReturn(true);

        useCaseComParametrosDdr.executar(sessao, enteId, 2026);

        verify(encerrarDdr).executar(
                eq(sessao), eq(enteId), eq(2026), eq(periodoExercicio.id()), any(), eq(parametrosDdr));
        verify(transporDdrAbertura).executar(eq(sessao), eq(enteId), eq(2026), eq(parametrosAberturaDdr));
    }

    @Test
    @DisplayName("mês 13 inexistente retorna o contrato de período não encontrado")
    void rejeitaPeriodoExercicioInexistente() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026))
                .isInstanceOf(PeriodoContabilNaoEncontradoException.class);

        verify(inscreverRP, never()).executar(any(), any(), anyInt(), any(), any(), any());
        verifyNoInteractions(
                apurarResultadoPatrimonial, encerrarContasOrcamentarias, encerrarDdr, transporSaldosAbertura,
                transporDdrAbertura);
        verify(periodoRepositorio, never()).encerrar(any());
    }

    @Test
    @DisplayName("mês 13 já encerrado mantém o erro de conflito sem gerar novos fatos")
    void rejeitaExercicioJaEncerrado() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodoExercicio = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(),
                enteId,
                2026,
                13,
                StatusPeriodo.ENCERRADO,
                Optional.of(Instant.parse("2026-12-31T23:00:00Z")));
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodoExercicio));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026))
                .isInstanceOf(EncerramentoConflitanteException.class);

        verify(inscreverRP, never()).executar(any(), any(), anyInt(), any(), any(), any());
        verifyNoInteractions(
                apurarResultadoPatrimonial, encerrarContasOrcamentarias, encerrarDdr, transporSaldosAbertura,
                transporDdrAbertura);
        verify(periodoRepositorio, never()).encerrar(any());
    }

    @Test
    @DisplayName("corrida: encerrar() retorna false levanta EncerramentoConflitanteException")
    void rejeitaCorrida() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        PeriodoContabil periodo = PeriodoContabil.reidratar(
                PeriodoContabilId.novo(), enteId, 2026, 13, StatusPeriodo.ABERTO, Optional.empty());
        when(periodoRepositorio.buscarPorCompetencia(enteId, 2026, 13)).thenReturn(Optional.of(periodo));
        when(periodoRepositorio.encerrar(any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026))
                .isInstanceOf(EncerramentoConflitanteException.class);

        verifyNoInteractions(transporSaldosAbertura, transporDdrAbertura);
    }

    @Test
    @DisplayName("RAZ-33 deny: RBAC nega o encerramento de exercício sem tocar repositórios")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026)).isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(
                periodoRepositorio, apurarResultadoPatrimonial, inscreverRP, encerrarContasOrcamentarias,
                encerrarDdr, transporSaldosAbertura, transporDdrAbertura, auditoria);
    }

    @Test
    @DisplayName("RAZ-33: ENCERRAR exige MFA antes de consultar o período")
    void negaSemMfa() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, RECURSO_PERIODO, Acao.ENCERRAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026)).isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(
                periodoRepositorio, apurarResultadoPatrimonial, inscreverRP, encerrarContasOrcamentarias,
                encerrarDdr, transporSaldosAbertura, transporDdrAbertura, auditoria);
    }

    @Test
    @DisplayName("RAZ-33: tenant divergente nunca consulta RBAC nem repositórios")
    void negaTenantDivergente() {
        Sessao sessaoDeOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(sessaoDeOutroEnte, enteId, 2026))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(
                periodoRepositorio, apurarResultadoPatrimonial, inscreverRP, encerrarContasOrcamentarias,
                encerrarDdr, transporSaldosAbertura, transporDdrAbertura, auditoria);
    }

    @Test
    @DisplayName("ADR-0045: tipo de evento dedicado para fatos append-only de encerramento")
    void tipoEventoEncerramentoTemCodigoEstavel() {
        assertThat(TipoEvento.ENCERRAMENTO.codigo()).isEqualTo("encerramento");
        assertThat(TipoEvento.deCodigo("encerramento")).isEqualTo(TipoEvento.ENCERRAMENTO);
    }

    @Test
    @DisplayName("RAZ-207: tipos de evento de RP têm códigos estáveis no enum")
    void tiposEventoRpTemCodigosEstaveis() {
        assertThat(TipoEvento.INSCRICAO_RESTOS_A_PAGAR.codigo()).isEqualTo("inscricao_restos_a_pagar");
        assertThat(TipoEvento.CANCELAMENTO_RESTOS_A_PAGAR.codigo()).isEqualTo("cancelamento_restos_a_pagar");
        assertThat(TipoEvento.deCodigo("inscricao_restos_a_pagar")).isEqualTo(TipoEvento.INSCRICAO_RESTOS_A_PAGAR);
        assertThat(TipoEvento.deCodigo("cancelamento_restos_a_pagar")).isEqualTo(TipoEvento.CANCELAMENTO_RESTOS_A_PAGAR);
    }

    @Test
    @DisplayName("RAZ-244: tipo de evento de encerramento da DDR tem código estável no enum")
    void tipoEventoEncerramentoDdrTemCodigoEstavel() {
        assertThat(TipoEvento.ENCERRAMENTO_DDR.codigo()).isEqualTo("encerramento_ddr");
        assertThat(TipoEvento.deCodigo("encerramento_ddr")).isEqualTo(TipoEvento.ENCERRAMENTO_DDR);
    }

    @Test
    @DisplayName("RAZ-259: tipo de evento de abertura tem código estável no enum")
    void tipoEventoAberturaTemCodigoEstavel() {
        assertThat(TipoEvento.ABERTURA.codigo()).isEqualTo("abertura");
        assertThat(TipoEvento.deCodigo("abertura")).isEqualTo(TipoEvento.ABERTURA);
    }
}
