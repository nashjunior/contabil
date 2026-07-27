package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.EmpenhoNaoEncontradoException;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ConsultarEmpenhoPorIdTest {

    private static final Recurso RECURSO = new Recurso("execucao:empenho");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private EmpenhoRepository repositorio;

    private final TenantId enteId = new TenantId(UUID.randomUUID());
    private final EmpenhoId empenhoId = EmpenhoId.novo();
    private final Cpf solicitante = new Cpf("55566677788");

    private ConsultarEmpenhoPorId useCase() {
        return new ConsultarEmpenhoPorId(new ControleAcesso(servicoIdentidade), repositorio);
    }

    private Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(), solicitante, enteId, Optional.empty(), false, Instant.parse("2030-01-01T00:00:00Z"));
    }

    private Empenho registrado() {
        return Empenho.registrar(
                empenhoId,
                enteId,
                1L,
                2026,
                TipoEmpenho.ORDINARIO,
                DotacaoId.novo(),
                CredorId.novo(),
                UnidadeGestoraId.novo(),
                null,
                Dinheiro.de("1000.00"),
                LocalDate.of(2026, 7, 20),
                "04.122.0001.2001",
                "0100000000",
                "empenho de material de expediente",
                new ReferenciaFatoContabil(UUID.randomUUID()),
                new Cpf("11122233344"));
    }

    @Test
    @DisplayName("exige Acao.LER antes de tocar o repositório")
    void exigeLerAntesDeQualquerIo() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(false);

        assertThatThrownBy(() -> useCase().executar(sessao(), enteId, empenhoId))
                .isInstanceOf(SemPermissaoException.class);

        verify(repositorio, never()).buscarPorId(any(), any());
    }

    @Test
    @DisplayName("devolve o agregado completo quando encontrado, escopado por ente/RLS")
    void devolveOAgregadoQuandoEncontrado() {
        Empenho empenho = registrado();
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho));

        Empenho resultado = useCase().executar(sessao(), enteId, empenhoId);

        assertThat(resultado).isSameAs(empenho);
    }

    @Test
    @DisplayName("id inexistente para o ente vira empenho_nao_encontrado, não documento_nao_encontrado")
    void idInexistenteViraEmpenhoNaoEncontrado() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().executar(sessao(), enteId, empenhoId))
                .isInstanceOf(EmpenhoNaoEncontradoException.class);
    }
}
