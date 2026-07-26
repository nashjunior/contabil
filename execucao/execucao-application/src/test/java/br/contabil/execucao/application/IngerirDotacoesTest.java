package br.contabil.execucao.application;

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
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.CreditoAdicional;
import br.contabil.execucao.domain.Dotacao;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.TipoCreditoAdicional;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.DotacaoRepository;
import br.contabil.execucao.domain.repository.DotacaoRepository.ErroItemLote;
import br.contabil.execucao.domain.repository.DotacaoRepository.ResultadoLote;
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
class IngerirDotacoesTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private DotacaoRepository repositorio;

    @Mock
    private AuditoriaEscrita auditoria;

    private IngerirDotacoes useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final UnidadeGestoraId unidadeGestoraId = UnidadeGestoraId.novo();
    private static final Recurso RECURSO = new Recurso("execucao:dotacao");

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
        useCase = new IngerirDotacoes(new ControleAcesso(servicoIdentidade), repositorio, auditoria, relogioFixo);
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

    private IngerirDotacoes.SolicitacaoFixacaoDotacao fixacaoValida() {
        return new IngerirDotacoes.SolicitacaoFixacaoDotacao(
                2026, "04.122.0001.2001", "0100000000", unidadeGestoraId, Dinheiro.de("100000.00"));
    }

    private void mockInserirEcoando() {
        when(repositorio.inserirEmLote(any())).thenAnswer(inv -> {
            List<Dotacao> lista = inv.getArgument(0);
            return new ResultadoLote<>(lista.stream().map(Dotacao::id).toList(), List.of());
        });
    }

    private void mockAplicarCreditoEcoando() {
        when(repositorio.aplicarCreditosEmLote(any(), any())).thenAnswer(inv -> {
            List<CreditoAdicional> lista = inv.getArgument(1);
            return new ResultadoLote<>(lista.stream().map(CreditoAdicional::dotacaoId).toList(), List.of());
        });
    }

    @Test
    @DisplayName("insere fixações e aplica créditos válidos, registrando um evento de auditoria resumindo o lote")
    void ingereLoteComSucesso() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.CRIAR))).thenReturn(true);
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.ALTERAR))).thenReturn(true);
        mockInserirEcoando();
        mockAplicarCreditoEcoando();

        DotacaoId dotacaoExistente = DotacaoId.novo();
        CreditoAdicional credito = new CreditoAdicional(
                dotacaoExistente, TipoCreditoAdicional.SUPLEMENTAR, Dinheiro.de("5000.00"), "decreto 123/2026");

        IngerirDotacoes.Resultado resultado =
                useCase.executar(sessao(true), enteId, List.of(fixacaoValida()), List.of(credito));

        assertThat(resultado.dotacoesInseridas()).hasSize(1);
        assertThat(resultado.dotacoesAtualizadas()).containsExactly(dotacaoExistente);
        assertThat(resultado.erros()).isEmpty();

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_dotacao_ingerida");
        assertThat(evento.getValue().detalhes())
                .containsEntry("inseridas", "1")
                .containsEntry("atualizadas", "1")
                .containsEntry("erros", "0")
                .containsEntry(
                        "creditosAdicionaisAplicados",
                        "%s:suplementar:decreto 123/2026".formatted(dotacaoExistente.valor()));
    }

    @Test
    @DisplayName("fail-soft: fixação inválida vira erro sem impedir a fixação válida do mesmo lote")
    void failSoftFixacaoInvalidaNaoDerrubaLote() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.CRIAR))).thenReturn(true);
        mockInserirEcoando();

        IngerirDotacoes.SolicitacaoFixacaoDotacao invalida = new IngerirDotacoes.SolicitacaoFixacaoDotacao(
                2026, "04.122.0001.2001", "0100000000", unidadeGestoraId, Dinheiro.zero());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Dotacao>> captor = ArgumentCaptor.forClass(List.class);

        IngerirDotacoes.Resultado resultado =
                useCase.executar(sessao(true), enteId, List.of(invalida, fixacaoValida()), List.of());

        verify(repositorio).inserirEmLote(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(resultado.dotacoesInseridas()).hasSize(1);
        assertThat(resultado.erros()).hasSize(1);
        assertThat(resultado.erros().get(0).motivo()).contains("positivo");
        verify(repositorio, never()).aplicarCreditosEmLote(any(), any());
    }

    @Test
    @DisplayName("fail-soft: crédito adicional com valor inválido vira erro sem impedir os demais")
    void failSoftCreditoInvalidoNaoDerrubaLote() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.ALTERAR))).thenReturn(true);
        mockAplicarCreditoEcoando();

        CreditoAdicional invalido = new CreditoAdicional(
                DotacaoId.novo(), TipoCreditoAdicional.SUPLEMENTAR, Dinheiro.de("-10.00"), "decreto inválido");
        CreditoAdicional valido = new CreditoAdicional(
                DotacaoId.novo(), TipoCreditoAdicional.ESPECIAL, Dinheiro.de("2000.00"), "decreto 456/2026");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreditoAdicional>> captor = ArgumentCaptor.forClass(List.class);

        IngerirDotacoes.Resultado resultado =
                useCase.executar(sessao(true), enteId, List.of(), List.of(invalido, valido));

        verify(repositorio).aplicarCreditosEmLote(eq(enteId), captor.capture());
        assertThat(captor.getValue()).containsExactly(valido);
        assertThat(resultado.dotacoesAtualizadas()).containsExactly(valido.dotacaoId());
        assertThat(resultado.erros()).hasSize(1);
        verify(repositorio, never()).inserirEmLote(any());
    }

    @Test
    @DisplayName("erros de persistência do repositório (ex.: dotação não encontrada) chegam ao resultado final")
    void propagaErrosDoRepositorioNoResultado() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.ALTERAR))).thenReturn(true);
        DotacaoId dotacaoInexistente = DotacaoId.novo();
        CreditoAdicional credito = new CreditoAdicional(
                dotacaoInexistente, TipoCreditoAdicional.SUPLEMENTAR, Dinheiro.de("100.00"), "decreto 789/2026");
        when(repositorio.aplicarCreditosEmLote(eq(enteId), any()))
                .thenReturn(new ResultadoLote<>(
                        List.of(),
                        List.of(new ErroItemLote(dotacaoInexistente.valor().toString(), "dotação não encontrada"))));

        IngerirDotacoes.Resultado resultado = useCase.executar(sessao(true), enteId, List.of(), List.of(credito));

        assertThat(resultado.dotacoesAtualizadas()).isEmpty();
        assertThat(resultado.erros()).hasSize(1);
        assertThat(resultado.erros().get(0).motivo()).isEqualTo("dotação não encontrada");
    }

    @Test
    @DisplayName("RBAC cobra só CRIAR quando o lote não tem créditos adicionais")
    void naoCobraAlterarQuandoLoteSoTemFixacao() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.CRIAR))).thenReturn(true);
        mockInserirEcoando();

        useCase.executar(sessao(true), enteId, List.of(fixacaoValida()), List.of());

        verify(servicoIdentidade, never()).autorizar(any(), any(), eq(Acao.ALTERAR));
    }

    @Test
    @DisplayName("lote vazio não toca RBAC, repositório nem auditoria")
    void loteVazioNaoFazNada() {
        IngerirDotacoes.Resultado resultado = useCase.executar(sessao(true), enteId, List.of(), List.of());

        assertThat(resultado.dotacoesInseridas()).isEmpty();
        assertThat(resultado.dotacoesAtualizadas()).isEmpty();
        assertThat(resultado.erros()).isEmpty();
        verifyNoInteractions(servicoIdentidade, repositorio, auditoria);
    }

    @Test
    @DisplayName("RBAC/MFA bloqueia antes de qualquer persistência")
    void negaSemMfaAntesDeIo() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.CRIAR))).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(sessao(false), enteId, List.of(fixacaoValida()), List.of()))
                .isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(repositorio, auditoria);
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

        assertThatThrownBy(() -> useCase.executar(sessaoOutroEnte, enteId, List.of(fixacaoValida()), List.of()))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(repositorio, auditoria);
    }
}
