package br.contabil.razao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.repository.BalancetePort;

@ExtendWith(MockitoExtension.class)
class GerarBalanceteTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private BalancetePort balancetePort;

    private GerarBalancete useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new GerarBalancete(new ControleAcesso(servicoIdentidade), balancetePort);
    }

    @Test
    @DisplayName("devolve o balancete do port quando o RBAC autoriza — LER nunca exige MFA")
    void devolveBalanceteAutorizado() {
        Sessao sessao = sessaoSemMfa();
        Balancete esperado = new Balancete(enteId, 2026, 1, List.of());
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:balancete"), ServicoIdentidade.Acao.LER))
                .thenReturn(true);
        when(balancetePort.balancete(enteId, 2026, 1)).thenReturn(esperado);

        Balancete balancete = useCase.executar(sessao, enteId, 2026, 1);

        assertThat(balancete).isSameAs(esperado);
    }

    @Test
    @DisplayName("RBAC nega a consulta — SemPermissaoException sem tocar no port")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:balancete"), ServicoIdentidade.Acao.LER))
                .thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026, 1)).isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(balancetePort);
    }

    @Test
    @DisplayName("tenant da requisição divergente da sessão nunca consulta o RBAC nem o port")
    void negaTenantDivergente() {
        Sessao sessaoDeOutroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                false,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(sessaoDeOutroEnte, enteId, 2026, 1))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(balancetePort);
    }
}
