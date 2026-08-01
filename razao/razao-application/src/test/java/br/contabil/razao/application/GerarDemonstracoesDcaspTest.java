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
import java.util.Map;
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
import br.contabil.razao.domain.BalancoFinanceiro;
import br.contabil.razao.domain.BalancoOrcamentario;
import br.contabil.razao.domain.BalancoPatrimonial;
import br.contabil.razao.domain.DemonstracoesDcasp;
import br.contabil.razao.domain.Dvp;
import br.contabil.razao.domain.LinhaDemonstracaoDcasp;
import br.contabil.razao.domain.repository.DemonstracoesDcaspPort;

@ExtendWith(MockitoExtension.class)
class GerarDemonstracoesDcaspTest {

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private DemonstracoesDcaspPort demonstracoesPort;

    private GerarDemonstracoesDcasp useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    private Sessao sessaoSemMfa() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        useCase = new GerarDemonstracoesDcasp(new ControleAcesso(servicoIdentidade), demonstracoesPort);
    }

    @Test
    @DisplayName("devolve as demonstrações DCASP do port quando o RBAC autoriza — LER nunca exige MFA")
    void devolveDemonstracoesAutorizadas() {
        Sessao sessao = sessaoSemMfa();
        DemonstracoesDcasp esperado = demonstracoes();
        when(servicoIdentidade.autorizar(
                        sessao, new Recurso("razao:demonstracoes_dcasp"), ServicoIdentidade.Acao.LER))
                .thenReturn(true);
        when(demonstracoesPort.demonstracoes(enteId, 2026)).thenReturn(esperado);

        DemonstracoesDcasp demonstracoes = useCase.executar(sessao, enteId, 2026);

        assertThat(demonstracoes).isSameAs(esperado);
    }

    @Test
    @DisplayName("RBAC nega a consulta DCASP — SemPermissaoException sem tocar no port")
    void negaSemAutorizacaoDoRbac() {
        Sessao sessao = sessaoSemMfa();
        when(servicoIdentidade.autorizar(
                        sessao, new Recurso("razao:demonstracoes_dcasp"), ServicoIdentidade.Acao.LER))
                .thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, 2026)).isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(demonstracoesPort);
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

        assertThatThrownBy(() -> useCase.executar(sessaoDeOutroEnte, enteId, 2026))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(demonstracoesPort);
    }

    private DemonstracoesDcasp demonstracoes() {
        LinhaDemonstracaoDcasp linha = new LinhaDemonstracaoDcasp(
                "1", "Total", Map.of("exercicioAtual", Dinheiro.de("100.00")));
        return new DemonstracoesDcasp(
                enteId,
                2026,
                new BalancoOrcamentario(enteId, 2026, List.of(linha)),
                new BalancoFinanceiro(enteId, 2026, List.of(linha)),
                new BalancoPatrimonial(enteId, 2026, List.of(linha)),
                new Dvp(enteId, 2026, List.of(linha)));
    }
}
