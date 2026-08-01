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
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PartidasNaoBalanceadasException;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.PeriodoEncerradoException;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

@ExtendWith(MockitoExtension.class)
class RegistrarFatoContabilTest {

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

    private RegistrarFatoContabil useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final PeriodoContabilId periodoId = PeriodoContabilId.novo();
    private final LocalDate dataCompetencia = LocalDate.of(2026, 7, 1);
    private static final ServicoIdentidade.Recurso RECURSO = new ServicoIdentidade.Recurso("razao:fato_contabil");

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);
        useCase = new RegistrarFatoContabil(
                new ControleAcesso(servicoIdentidade), repositorio, contadorFato, periodoContabil, auditoria, relogioFixo);
    }

    private List<Lancamento> lancamentosBalanceados() {
        return List.of(
                Lancamento.de(ContaContabilId.novo(), Natureza.DEBITO, Dinheiro.de("500.00")),
                Lancamento.de(ContaContabilId.novo(), Natureza.CREDITO, Dinheiro.de("500.00")));
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
    @DisplayName("obtém período aberto e numero_seq, monta o fato e insere no repositório")
    void registraComSucesso() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(periodoContabil.periodoAbertoPara(enteId, dataCompetencia)).thenReturn(periodoId);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(42L);

        FatoContabil fato = useCase.executar(
                sessao, enteId, dataCompetencia, TipoEvento.RECEITA, "historico", "origem", lancamentosBalanceados());

        assertThat(fato.numeroSeq()).isEqualTo(42L);
        assertThat(fato.periodoId()).isEqualTo(periodoId);
        verify(repositorio).inserir(fato);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("razao_fato_contabil_registrado");
        assertThat(evento.getValue().detalhes())
                .containsEntry("tipoEvento", "RECEITA")
                .containsEntry("origem", "origem")
                .containsEntry("dataCompetencia", "2026-07-01");
    }

    @Test
    @DisplayName("falha rápido em Σdébito != Σcrédito sem consultar período/contador/repositório")
    void falhaRapidoSemBalancear() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);

        List<Lancamento> desbalanceado = List.of(
                Lancamento.de(ContaContabilId.novo(), Natureza.DEBITO, Dinheiro.de("500.00")),
                Lancamento.de(ContaContabilId.novo(), Natureza.CREDITO, Dinheiro.de("400.00")));

        assertThatThrownBy(() -> useCase.executar(
                        sessao, enteId, dataCompetencia, TipoEvento.RECEITA, "historico", "origem", desbalanceado))
                .isInstanceOf(PartidasNaoBalanceadasException.class);

        verify(periodoContabil, never()).periodoAbertoPara(any(), any());
        verify(contadorFato, never()).proximoNumeroSeq(any());
        verify(repositorio, never()).inserir(any());
    }

    @Test
    @DisplayName("Regra 5: período encerrado rejeita o registro antes de obter numero_seq")
    void rejeitaPeriodoEncerrado() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);
        when(periodoContabil.periodoAbertoPara(enteId, dataCompetencia))
                .thenThrow(new PeriodoEncerradoException(enteId, dataCompetencia));

        assertThatThrownBy(() -> useCase.executar(
                        sessao,
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

    @Test
    @DisplayName("RAZ-33 deny: RBAC nega o registro — SemPermissaoException sem tocar em período/contador/repositório")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(
                        sessao, enteId, dataCompetencia, TipoEvento.RECEITA, "historico", "origem",
                        lancamentosBalanceados()))
                .isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(repositorio, contadorFato, periodoContabil, auditoria);
    }

    @Test
    @DisplayName("RAZ-33: CRIAR movimenta recurso — MFA ausente falha antes de validar Σ=Σ")
    void negaSemMfa() {
        Sessao sessao = sessao(false);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.CRIAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(
                        sessao, enteId, dataCompetencia, TipoEvento.RECEITA, "historico", "origem",
                        lancamentosBalanceados()))
                .isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(repositorio, contadorFato, periodoContabil, auditoria);
    }

    @Test
    @DisplayName("RAZ-33: tenant da requisição divergente da sessão nunca consulta o RBAC nem o repositório")
    void negaTenantDivergente() {
        Sessao sessaoDeOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(
                        sessaoDeOutroEnte, enteId, dataCompetencia, TipoEvento.RECEITA, "historico", "origem",
                        lancamentosBalanceados()))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(repositorio, contadorFato, periodoContabil, auditoria);
    }
}
