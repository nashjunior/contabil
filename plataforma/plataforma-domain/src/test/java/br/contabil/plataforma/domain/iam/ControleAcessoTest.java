package br.contabil.plataforma.domain.iam;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ControleAcessoTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    private ControleAcesso controleAcesso;

    private final TenantId ente = TenantId.de(UUID.randomUUID().toString());
    private final Recurso recurso = new Recurso("razao:fato_contabil");

    @BeforeEach
    void setUp() {
        controleAcesso = new ControleAcesso(servicoIdentidade);
    }

    private Sessao sessao(TenantId enteDaSessao, boolean mfaConcluido) {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                enteDaSessao,
                Optional.empty(),
                mfaConcluido,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("permit: RBAC concede e MFA concluído — ação que movimenta recurso passa")
    void permiteQuandoAutorizadoEComMfa() {
        Sessao sessao = sessao(ente, true);
        when(servicoIdentidade.autorizar(sessao, recurso, Acao.CRIAR)).thenReturn(true);

        assertThatCode(() -> controleAcesso.exigir(sessao, ente, recurso, Acao.CRIAR))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("permit: ação de leitura não exige MFA mesmo sem MFA concluído")
    void leituraNaoExigeMfa() {
        Sessao sessao = sessao(ente, false);
        when(servicoIdentidade.autorizar(sessao, recurso, Acao.LER)).thenReturn(true);

        assertThatCode(() -> controleAcesso.exigir(sessao, ente, recurso, Acao.LER))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deny: RBAC nega — SemPermissaoException, sem checar MFA depois")
    void negaQuandoRbacNega() {
        Sessao sessao = sessao(ente, true);
        when(servicoIdentidade.autorizar(sessao, recurso, Acao.ESTORNAR)).thenReturn(false);

        assertThatThrownBy(() -> controleAcesso.exigir(sessao, ente, recurso, Acao.ESTORNAR))
                .isInstanceOf(SemPermissaoException.class);
    }

    @Test
    @DisplayName("deny: tenant da requisição diverge do tenant da sessão — nunca consulta o RBAC (anti-BOLA)")
    void negaQuandoTenantDivergeSemConsultarRbac() {
        Sessao sessaoDeOutroEnte = sessao(TenantId.de(UUID.randomUUID().toString()), true);

        assertThatThrownBy(() -> controleAcesso.exigir(sessaoDeOutroEnte, ente, recurso, Acao.CRIAR))
                .isInstanceOf(SemPermissaoException.class)
                .hasMessageContaining("tenant");

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
    }

    @Test
    @DisplayName("MFA ausente: RBAC concede mas sessão não concluiu MFA — ação movimenta recurso, MfaRequeridoException")
    void exigeMfaParaAcaoQueMovimentaRecurso() {
        Sessao semMfa = sessao(ente, false);
        when(servicoIdentidade.autorizar(semMfa, recurso, Acao.APROVAR)).thenReturn(true);

        assertThatThrownBy(() -> controleAcesso.exigir(semMfa, ente, recurso, Acao.APROVAR))
                .isInstanceOf(MfaRequeridoException.class);
    }
}
