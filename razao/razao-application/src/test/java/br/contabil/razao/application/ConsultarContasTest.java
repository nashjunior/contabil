package br.contabil.razao.application;

import java.time.Instant;
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
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ContaResumo;
import br.contabil.razao.domain.PaginaContas;
import br.contabil.razao.domain.repository.CatalogoContasPort;

@ExtendWith(MockitoExtension.class)
class ConsultarContasTest {

    private static final Recurso RECURSO = new Recurso("razao:conta_pcasp");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private CatalogoContasPort catalogo;

    private ConsultarContas useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());

    private Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    private PaginaContas umaPagina() {
        return new PaginaContas(
                List.of(new ContaResumo(
                        ContaContabilId.novo(), "1.1.1", "Caixa e bancos", "D", "patrimonial", true, null)),
                Optional.of("cursorOpaco"));
    }

    @BeforeEach
    void setUp() {
        useCase = new ConsultarContas(new ControleAcesso(servicoIdentidade), catalogo);
    }

    @Test
    @DisplayName("autorizado: delega ao catálogo e devolve a página — LER nunca exige MFA")
    void delegaQuandoAutorizado() {
        Sessao sessao = sessao();
        PaginaContas pagina = umaPagina();
        when(servicoIdentidade.autorizar(sessao, RECURSO, ServicoIdentidade.Acao.LER)).thenReturn(true);
        when(catalogo.buscar(eq(enteId), eq(Optional.of("1.1")), eq(20), eq(Optional.empty()))).thenReturn(pagina);

        PaginaContas resultado =
                useCase.executar(sessao, enteId, Optional.of("1.1"), Optional.empty(), Optional.empty());

        assertThat(resultado).isSameAs(pagina);
    }

    @Test
    @DisplayName("limit ausente = 20; acima de 100 clampa em 100; abaixo de 1 clampa em 1")
    void clampaLimite() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, ServicoIdentidade.Acao.LER)).thenReturn(true);
        when(catalogo.buscar(any(), any(), any(Integer.class), any())).thenReturn(umaPagina());

        useCase.executar(sessao, enteId, Optional.empty(), Optional.empty(), Optional.empty());
        verify(catalogo).buscar(enteId, Optional.empty(), 20, Optional.empty());

        useCase.executar(sessao, enteId, Optional.empty(), Optional.of(500), Optional.empty());
        verify(catalogo).buscar(enteId, Optional.empty(), 100, Optional.empty());

        useCase.executar(sessao, enteId, Optional.empty(), Optional.of(0), Optional.empty());
        verify(catalogo).buscar(enteId, Optional.empty(), 1, Optional.empty());
    }

    @Test
    @DisplayName("busca em branco vira filtro ausente (não busca por string vazia)")
    void buscaEmBrancoViraAusente() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, ServicoIdentidade.Acao.LER)).thenReturn(true);
        when(catalogo.buscar(any(), any(), any(Integer.class), any())).thenReturn(umaPagina());

        useCase.executar(sessao, enteId, Optional.of("   "), Optional.empty(), Optional.empty());

        verify(catalogo).buscar(enteId, Optional.empty(), 20, Optional.empty());
    }

    @Test
    @DisplayName("RAZ-33 deny: RBAC nega — SemPermissaoException sem tocar no catálogo")
    void negaSemAutorizacao() {
        Sessao sessao = sessao();
        when(servicoIdentidade.autorizar(sessao, RECURSO, ServicoIdentidade.Acao.LER)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(SemPermissaoException.class);

        verifyNoInteractions(catalogo);
    }

    @Test
    @DisplayName("RAZ-33: tenant divergente da sessão nunca consulta o RBAC nem o catálogo")
    void negaTenantDivergente() {
        Sessao outroEnte = new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(UUID.randomUUID().toString()),
                Optional.empty(),
                false,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThatThrownBy(() -> useCase.executar(outroEnte, enteId, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(SemPermissaoException.class);

        verify(servicoIdentidade, never()).autorizar(any(), any(), any());
        verifyNoInteractions(catalogo);
    }
}
