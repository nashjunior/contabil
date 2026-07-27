package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
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
import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoNaoEncontradoException;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoTenantInvalidoException;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.SemPermissaoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class ConsultarDocumentoEmpenhoTest {

    private static final Recurso RECURSO = new Recurso("execucao:empenho");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private EmpenhoRepository repositorio;

    @Mock
    private ArmazenamentoDocumentos armazenamento;

    private final TenantId enteId = new TenantId(UUID.randomUUID());
    private final EmpenhoId empenhoId = EmpenhoId.novo();
    private final Cpf solicitante = new Cpf("55566677788");

    private ConsultarDocumentoEmpenho useCase() {
        return new ConsultarDocumentoEmpenho(new ControleAcesso(servicoIdentidade), repositorio, armazenamento);
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

    private URI uriDoEnte(String sufixo) {
        return URI.create("s3://ged/%s/%s".formatted(enteId.valor(), sufixo));
    }

    @Test
    @DisplayName("exige Acao.LER antes de tocar repositório/armazenamento")
    void exigeLerAntesDeQualquerIo() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(false);

        assertThatThrownBy(() -> useCase().executar(sessao(), enteId, empenhoId))
                .isInstanceOf(SemPermissaoException.class);

        verify(repositorio, never()).buscarPorId(any(), any());
    }

    @Test
    @DisplayName("PENDENTE_ASSINATURA: lê o pendente quando não há documento assinado")
    void servePendenteQuandoNaoAssinado() {
        URI pendente = uriDoEnte("nota-empenho.pdf");
        Empenho empenho = registrado().marcarPendenteAssinatura(pendente);
        byte[] pdf = "pdf-pendente".getBytes();

        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho));
        when(armazenamento.ler(pendente)).thenReturn(pdf);

        byte[] resultado = useCase().executar(sessao(), enteId, empenhoId);

        assertThat(resultado).isSameAs(pdf);
    }

    @Test
    @DisplayName("ASSINADO: lê o assinado, não o pendente, mesmo com os dois presentes")
    void serveAssinadoEmVezDoPendenteQuandoAmbosPresentes() {
        URI pendente = uriDoEnte("nota-empenho.pdf");
        URI assinado = uriDoEnte("nota-empenho-assinado.pdf");
        DocumentoAssinadoEmpenho documentoAssinado = new DocumentoAssinadoEmpenho(
                assinado, "hash", "manifesto", UUID.randomUUID(), NivelAssinatura.AVANCADA_GOVBR,
                new Cpf("33333333333"), Instant.parse("2026-07-26T10:00:00Z"));
        Empenho empenho = registrado().marcarPendenteAssinatura(pendente).assinar(documentoAssinado);
        byte[] pdf = "pdf-assinado".getBytes();

        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho));
        when(armazenamento.ler(assinado)).thenReturn(pdf);

        byte[] resultado = useCase().executar(sessao(), enteId, empenhoId);

        assertThat(resultado).isSameAs(pdf);
        verify(armazenamento, never()).ler(pendente);
    }

    @Test
    @DisplayName("empenho inexistente vira documento_nao_encontrado (404), não empenho_nao_encontrado")
    void empenhoInexistenteViraDocumentoNaoEncontrado() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().executar(sessao(), enteId, empenhoId))
                .isInstanceOf(DocumentoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("REGISTRADO sem documento (worker ainda não gerou o pendente) vira documento_nao_encontrado")
    void semDocumentoAindaViraDocumentoNaoEncontrado() {
        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(registrado()));

        assertThatThrownBy(() -> useCase().executar(sessao(), enteId, empenhoId))
                .isInstanceOf(DocumentoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("R3/RAZ-45: URI persistida fora do prefixo do ente nunca chega a ArmazenamentoDocumentos.ler")
    void uriCrossTenantEBloqueadaAntesDoObjectStore() {
        TenantId outroEnte = new TenantId(UUID.randomUUID());
        URI uriDeOutroEnte = URI.create("s3://ged/%s/nota-empenho.pdf".formatted(outroEnte.valor()));
        Empenho empenho = registrado().marcarPendenteAssinatura(uriDeOutroEnte);

        when(servicoIdentidade.autorizar(any(), eq(RECURSO), eq(Acao.LER))).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho));

        assertThatThrownBy(() -> useCase().executar(sessao(), enteId, empenhoId))
                .isInstanceOf(DocumentoTenantInvalidoException.class);

        verify(armazenamento, never()).ler(any());
    }
}
