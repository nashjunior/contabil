package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

@ExtendWith(MockitoExtension.class)
class EstornarFatoContabilTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

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

    private Sessao sessaoComMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), true, Instant.parse("2030-01-01T00:00:00Z"));
    }

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new EstornarFatoContabil(
                new ControleAcesso(servicoIdentidade), repositorio, contadorFato, periodoContabil, relogioFixo);
    }

    @Test
    @DisplayName("estorna um fato existente criando um NOVO fato com lançamentos invertidos")
    void estornaFatoExistente() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, new ServicoIdentidade.Recurso("razao:fato_contabil"), Acao.ESTORNAR))
                .thenReturn(true);

        FatoContabil original = FatoContabil.registrar(
                enteId,
                1L,
                LocalDate.of(2026, Month.JULY, 1),
                periodoId,
                TipoEvento.RECEITA,
                "original",
                "origem",
                List.of(
                        Lancamento.de(UUID.randomUUID(), Natureza.DEBITO, Dinheiro.de("300.00")),
                        Lancamento.de(UUID.randomUUID(), Natureza.CREDITO, Dinheiro.de("300.00"))),
                relogioFixo);

        when(repositorio.buscarPorId(enteId, original.id())).thenReturn(Optional.of(original));
        when(periodoContabil.periodoAbertoPara(enteId, LocalDate.of(2026, Month.JULY, 19))).thenReturn(periodoId);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(2L);

        FatoContabil estorno =
                useCase.executar(sessao, enteId, original.id(), LocalDate.of(2026, Month.JULY, 19), "correção", "origem");

        assertThat(estorno.isEstorno()).isTrue();
        assertThat(estorno.fatoEstornadoId()).isEqualTo(original.id());
        assertThat(estorno.numeroSeq()).isEqualTo(2L);
    }

    @Test
    @DisplayName("rejeita estorno de fato inexistente")
    void rejeitaFatoInexistente() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, new ServicoIdentidade.Recurso("razao:fato_contabil"), Acao.ESTORNAR))
                .thenReturn(true);

        UUID idInexistente = UUID.randomUUID();
        when(repositorio.buscarPorId(enteId, idInexistente)).thenReturn(Optional.empty());
        LocalDate dataEstorno = LocalDate.of(2026, Month.JULY, 19);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, idInexistente, dataEstorno, "correção", "origem"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("RAZ-33 deny: RBAC nega o estorno — SemPermissaoException sem tocar no repositório")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoComMfa();
        when(servicoIdentidade.autorizar(sessao, new ServicoIdentidade.Recurso("razao:fato_contabil"), Acao.ESTORNAR))
                .thenReturn(false);

        UUID fatoId = UUID.randomUUID();
        LocalDate dataEstorno = LocalDate.of(2026, Month.JULY, 19);
        assertThatThrownBy(() -> useCase.executar(sessao, enteId, fatoId, dataEstorno, "correção", "origem"))
                .isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(repositorio, contadorFato, periodoContabil);
    }

    @Test
    @DisplayName("RAZ-33: ESTORNAR movimenta recurso — MFA ausente falha antes de buscar o fato original")
    void negaSemMfa() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, new ServicoIdentidade.Recurso("razao:fato_contabil"), Acao.ESTORNAR))
                .thenReturn(true);

        UUID fatoId = UUID.randomUUID();
        LocalDate dataEstorno = LocalDate.of(2026, Month.JULY, 19);
        assertThatThrownBy(() -> useCase.executar(sessao, enteId, fatoId, dataEstorno, "correção", "origem"))
                .isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(repositorio, contadorFato, periodoContabil);
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

        UUID fatoId = UUID.randomUUID();
        LocalDate dataEstorno = LocalDate.of(2026, Month.JULY, 19);
        assertThatThrownBy(() -> useCase.executar(sessaoDeOutroEnte, enteId, fatoId, dataEstorno, "correção", "origem"))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(repositorio, contadorFato, periodoContabil);
    }
}
