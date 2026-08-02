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

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.InformacoesComplementares;
import br.contabil.razao.domain.LinhaMatrizSaldos;
import br.contabil.razao.domain.repository.MatrizSaldosContabeisPort;

@ExtendWith(MockitoExtension.class)
class GerarMatrizSaldosContabeisTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private MatrizSaldosContabeisPort matrizPort;

    private GerarMatrizSaldosContabeis useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new GerarMatrizSaldosContabeis(new ControleAcesso(servicoIdentidade), matrizPort);
    }

    @Test
    @DisplayName("devolve a matriz do port quando o RBAC autoriza — LER nunca exige MFA")
    void devolveMatrizAutorizada() {
        Sessao sessao = sessaoSemMfa();
        List<LinhaMatrizSaldos> esperado = List.of(new LinhaMatrizSaldos(
                ContaContabilId.novo(),
                "1.1.1.01.01",
                "Caixa",
                "D",
                InformacoesComplementares.nenhuma(),
                null,
                Dinheiro.zero(),
                Dinheiro.zero(),
                Dinheiro.zero(),
                Dinheiro.zero()));
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:matriz_saldos_contabeis"), ServicoIdentidade.Acao.LER))
                .thenReturn(true);
        when(matrizPort.matriz(enteId, 2026, 1)).thenReturn(esperado);

        List<LinhaMatrizSaldos> matriz = useCase.executar(sessao, enteId, 2026, 1);

        assertThat(matriz).isSameAs(esperado);
    }

    @Test
    @DisplayName("RBAC nega a consulta — SemPermissaoException sem tocar no port")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(sessao, new Recurso("razao:matriz_saldos_contabeis"), ServicoIdentidade.Acao.LER))
                .thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026, 1)).isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(matrizPort);
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
        verifyNoInteractions(matrizPort);
    }
}
