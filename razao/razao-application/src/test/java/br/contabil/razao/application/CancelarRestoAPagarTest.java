package br.contabil.razao.application;

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
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
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
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.CancelamentoRestosAPagarInvalidoException;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.FatoContabilId;
import br.contabil.razao.domain.FonteRecurso;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

@ExtendWith(MockitoExtension.class)
class CancelarRestoAPagarTest {

    private static final ServicoIdentidade.Recurso RECURSO = new ServicoIdentidade.Recurso("razao:fato_contabil");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private FatoContabilRepository repositorio;

    @Mock
    private ContadorFatoPort contadorFato;

    @Mock
    private PeriodoContabilPort periodoContabil;

    @Mock
    private AuditoriaEscrita auditoria;

    private CancelarRestoAPagar useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final LocalDate dataCompetencia = LocalDate.of(2027, 1, 15);
    private final Clock relogio = Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC);
    private final PeriodoContabilId periodoId = PeriodoContabilId.novo();

    @BeforeEach
    void setUp() {
        useCase = new CancelarRestoAPagar(
                new ControleAcesso(servicoIdentidade), repositorio, contadorFato, periodoContabil, auditoria, relogio);
    }

    private Sessao sessao(boolean mfa) {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), mfa,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    private FatoContabil fatoInscricaoFake() {
        ContaContabilId contaA = ContaContabilId.novo();
        ContaContabilId contaB = ContaContabilId.novo();
        FonteRecurso fonte = new FonteRecurso("100");
        List<Lancamento> lancamentos = List.of(
                Lancamento.de(contaA, Natureza.DEBITO, Dinheiro.de("500.00"), fonte),
                Lancamento.de(contaB, Natureza.CREDITO, Dinheiro.de("500.00"), fonte));
        return FatoContabil.registrar(
                enteId, 1L, LocalDate.of(2026, 12, 31), PeriodoContabilId.novo(),
                TipoEvento.INSCRICAO_RESTOS_A_PAGAR, "Inscrição RP 2026 — fonte 100",
                "EncerrarExercicio", lancamentos, relogio);
    }

    @Test
    @DisplayName("cancela RP inscrito: gera fato com lançamentos invertidos e CANCELAMENTO_RESTOS_A_PAGAR")
    void cancelaInscricaoComSucesso() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ESTORNAR)).thenReturn(true);

        FatoContabil inscricao = fatoInscricaoFake();
        when(repositorio.buscarPorId(enteId, inscricao.id())).thenReturn(Optional.of(inscricao));
        when(periodoContabil.periodoAbertoPara(enteId, dataCompetencia)).thenReturn(periodoId);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(2L);

        FatoContabil cancelamento = useCase.executar(
                sessao, enteId, inscricao.id(), dataCompetencia, "Cancelamento por insuficiência", "teste");

        assertThat(cancelamento.tipoEvento()).isEqualTo(TipoEvento.CANCELAMENTO_RESTOS_A_PAGAR);
        assertThat(cancelamento.fatoEstornadoId()).isEqualTo(inscricao.id());
        assertThat(cancelamento.lancamentos()).hasSize(2);

        // Lançamentos invertidos: naturezas opostas, mesmo valor
        assertThat(cancelamento.lancamentos())
                .anyMatch(l -> l.natureza() == Natureza.CREDITO && l.valor().equals(Dinheiro.de("500.00")))
                .anyMatch(l -> l.natureza() == Natureza.DEBITO && l.valor().equals(Dinheiro.de("500.00")));

        verify(repositorio).inserir(cancelamento);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("razao_cancelamento_restos_a_pagar");
        assertThat(evento.getValue().detalhes())
                .containsEntry("motivo", "Cancelamento por insuficiência")
                .containsEntry("fatoInscricaoId", inscricao.id().valor().toString());
    }

    @Test
    @DisplayName("rejeita cancelamento de fato que não é inscrição de RP")
    void rejeitaFatoNaoEhInscricaoRP() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ESTORNAR)).thenReturn(true);

        ContaContabilId c = ContaContabilId.novo();
        FatoContabil fatoEmpenho = FatoContabil.registrar(
                enteId, 1L, dataCompetencia, PeriodoContabilId.novo(),
                TipoEvento.EMPENHO, "empenho", "origem",
                List.of(
                        Lancamento.de(c, Natureza.DEBITO, Dinheiro.de("100.00")),
                        Lancamento.de(ContaContabilId.novo(), Natureza.CREDITO, Dinheiro.de("100.00"))),
                relogio);

        when(repositorio.buscarPorId(enteId, fatoEmpenho.id())).thenReturn(Optional.of(fatoEmpenho));

        assertThatThrownBy(() -> useCase.executar(
                        sessao, enteId, fatoEmpenho.id(), dataCompetencia, "motivo", "origem"))
                .isInstanceOf(CancelamentoRestosAPagarInvalidoException.class)
                .satisfies(e -> assertThat(((CancelamentoRestosAPagarInvalidoException) e).codigo())
                        .isEqualTo("cancelamento_restos_a_pagar_invalido"));

        verify(repositorio, never()).inserir(any());
        verify(auditoria, never()).append(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("fato inexistente lança NoSuchElementException antes de qualquer escrita")
    void rejeitaFatoInexistente() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ESTORNAR)).thenReturn(true);

        FatoContabilId idInexistente = FatoContabilId.novo();
        when(repositorio.buscarPorId(enteId, idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(
                        sessao, enteId, idInexistente, dataCompetencia, "motivo", "origem"))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(repositorio, never()).inserir(any());
    }

    @Test
    @DisplayName("RAZ-33 deny: ESTORNAR requer RBAC antes de qualquer leitura")
    void negaSemAutorizacaoRbac() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ESTORNAR)).thenReturn(false);
        FatoContabilId qualquer = FatoContabilId.novo();

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, qualquer, dataCompetencia, "motivo", "origem"))
                .isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(repositorio, contadorFato, periodoContabil, auditoria);
    }

    @Test
    @DisplayName("RAZ-33: ESTORNAR exige MFA — falha antes de buscar o fato")
    void negaSemMfa() {
        Sessao sessao = sessao(false);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ESTORNAR)).thenReturn(true);
        FatoContabilId qualquer = FatoContabilId.novo();

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, qualquer, dataCompetencia, "motivo", "origem"))
                .isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(repositorio, contadorFato, periodoContabil, auditoria);
    }
}
