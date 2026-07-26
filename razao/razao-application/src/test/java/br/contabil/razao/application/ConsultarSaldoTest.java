package br.contabil.razao.application;

import java.time.Instant;
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
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ContaNaoEncontradaException;
import br.contabil.razao.domain.repository.CatalogoContasPort;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;

@ExtendWith(MockitoExtension.class)
class ConsultarSaldoTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private ConsultaSaldoPort consultaSaldo;

    @Mock
    private CatalogoContasPort catalogo;

    private ConsultarSaldo useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final ContaContabilId contaId = ContaContabilId.novo();

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new ConsultarSaldo(new ControleAcesso(servicoIdentidade), consultaSaldo, catalogo);
    }

    @Test
    @DisplayName("devolve o saldo do port quando a conta existe e o RBAC autoriza — LER nunca exige MFA")
    void devolveSaldoAutorizado() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:saldo_conta"), ServicoIdentidade.Acao.LER))
                .thenReturn(true);
        when(catalogo.existe(enteId, contaId)).thenReturn(true);
        when(consultaSaldo.saldoDevedorLiquido(enteId, contaId)).thenReturn(Dinheiro.de("123.45"));

        Dinheiro saldo = useCase.executar(sessao, enteId, contaId);

        assertThat(saldo).isEqualTo(Dinheiro.de("123.45"));
    }

    @Test
    @DisplayName("RAZ-117 gap 2: conta inexistente lança conta_nao_encontrada sem ler o saldo (zero ≠ inexistente)")
    void contaInexistenteLancaContaNaoEncontrada() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:saldo_conta"), ServicoIdentidade.Acao.LER))
                .thenReturn(true);
        when(catalogo.existe(enteId, contaId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, contaId))
                .isInstanceOf(ContaNaoEncontradaException.class)
                .satisfies(e -> assertThat(((ContaNaoEncontradaException) e).codigo()).isEqualTo("conta_nao_encontrada"));

        verifyNoInteractions(consultaSaldo);
    }

    @Test
    @DisplayName("RAZ-33 deny: RBAC nega a consulta — SemPermissaoException sem tocar no catálogo nem no saldo")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:saldo_conta"), ServicoIdentidade.Acao.LER))
                .thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, contaId))
                .isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(catalogo);
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
        verifyNoInteractions(catalogo);
        verifyNoInteractions(consultaSaldo);
    }
}
