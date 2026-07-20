package br.contabil.razao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultarSaldoTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private ConsultaSaldoPort consultaSaldo;

    private ConsultarSaldo useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final UUID contaId = UUID.randomUUID();

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new ConsultarSaldo(new ControleAcesso(servicoIdentidade), consultaSaldo);
    }

    @Test
    @DisplayName("devolve o saldo do port quando o RBAC autoriza — LER nunca exige MFA")
    void devolveSaldoAutorizado() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:saldo_conta"), ServicoIdentidade.Acao.LER))
                .thenReturn(true);
        when(consultaSaldo.saldoDevedorLiquido(enteId, contaId)).thenReturn(Dinheiro.de("123.45"));

        Dinheiro saldo = useCase.executar(sessao, enteId, contaId);

        assertThat(saldo).isEqualTo(Dinheiro.de("123.45"));
    }

    @Test
    @DisplayName("RAZ-33 deny: RBAC nega a consulta — SemPermissaoException sem tocar no port")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:saldo_conta"), ServicoIdentidade.Acao.LER))
                .thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, contaId))
                .isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(consultaSaldo);
    }

    @Test
    @DisplayName("RAZ-33: tenant da requisição divergente da sessão nunca consulta o RBAC nem o port")
    void negaTenantDivergente() {
        Sessao sessaoDeOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                false,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(sessaoDeOutroEnte, enteId, contaId))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(consultaSaldo);
    }
}
