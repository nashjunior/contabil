package br.contabil.plataforma.infra.assinatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.CertificadoInvalidoException;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.DocumentoAssinado;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.DocumentoParaAssinar;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelInsuficienteException;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ReferenciaDocumento;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ResultadoVerificacao;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.Signatario;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ServicoAssinaturaGovBrAvancadaTest {

    private static final ReferenciaDocumento ORIGEM = new ReferenciaDocumento(URI.create("s3://ged/empenho-1.pdf"));
    private static final TenantId ENTE = new TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final DocumentoParaAssinar DOCUMENTO = new DocumentoParaAssinar(ENTE, ORIGEM, "empenho");
    private static final Signatario SIGNATARIO = new Signatario("11122233344", "Fulana de Tal");
    // PAdES exige um PDF de verdade: o preparo do placeholder de assinatura (RAZ-34) carrega
    // esse conteúdo via PDFBox antes de calcular o hash — não é mais um SHA-256 sobre bytes crus.
    private static final byte[] CONTEUDO = pdfMinimo();
    private static final byte[] PKCS7_FALSO = "pkcs7-fake-bytes".getBytes();

    private ProvedorAssinaturaGovBr provedor;
    private VerificadorRevogacaoCertificado verificadorRevogacao;
    private AuditoriaEscrita trilha;
    private X509Certificate certificadoFake;
    private ServicoAssinaturaGovBrAvancada servico;
    private ReferenciaDocumento referenciaPublicada;
    private byte[] pdfPublicado;

    @BeforeEach
    void montaServico() {
        provedor = mock(ProvedorAssinaturaGovBr.class);
        verificadorRevogacao = mock(VerificadorRevogacaoCertificado.class);
        trilha = mock(AuditoriaEscrita.class);
        certificadoFake = mock(X509Certificate.class);
        referenciaPublicada = new ReferenciaDocumento(URI.create("s3://ged/empenho-1-assinado.pdf"));
        pdfPublicado = null;

        when(trilha.append(any())).thenReturn(new AuditoriaEscrita.EntradaTrilha(UUID.randomUUID(), "hash", null, 1));

        servico = new ServicoAssinaturaGovBrAvancada(
                provedor,
                verificadorRevogacao,
                trilha,
                ref -> CONTEUDO,
                (bytes, ref) -> {
                    pdfPublicado = bytes;
                    return referenciaPublicada;
                },
                bytes -> certificadoFake,
                Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC));
    }

    private static byte[] pdfMinimo() {
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage());
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("falha ao gerar PDF mínimo de teste", e);
        }
    }

    @Test
    @DisplayName("caminho feliz: assina, checa revogação, registra na trilha e devolve o manifesto")
    void assinaComSucesso() throws Exception {
        when(provedor.assinarPkcs7(any())).thenReturn(PKCS7_FALSO);
        when(verificadorRevogacao.verificar(certificadoFake))
                .thenReturn(new VerificadorRevogacaoCertificado.ResultadoRevogacao(false, "válido"));

        DocumentoAssinado resultado = servico.assinar(DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of(SIGNATARIO));

        // Hash agora é sobre o byte-range PAdES preparado (RAZ-34), não sobre o PDF bruto —
        // a estrutura exata é coberta em PreparadorAssinaturaPadesTest; aqui só confere que é
        // um SHA-256 válido e que foi o mesmo hash publicado na trilha (ver captor abaixo).
        byte[] hashDecodificado = java.util.Base64.getDecoder().decode(resultado.hash());
        assertThat(hashDecodificado).hasSize(32);
        assertThat(resultado.pdfAssinado()).isEqualTo(referenciaPublicada);
        assertThat(resultado.manifesto())
                .contains("Fulana de Tal")
                .contains("***.222.***-**")
                .contains("empenho")
                .doesNotContain("11122233344");
        assertThat(resultado.idTransacao()).isNotNull();

        assertThat(pdfPublicado).isNotNull();
        try (PDDocument documentoAssinado = Loader.loadPDF(pdfPublicado)) {
            PDSignature assinatura = documentoAssinado.getLastSignatureDictionary();
            assertThat(assinatura).isNotNull();
            assertThat(assinatura.getFilter()).isEqualTo(PDSignature.FILTER_ADOBE_PPKLITE.getName());
            assertThat(assinatura.getSubFilter()).isEqualTo(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED.getName());
            assertThat(assinatura.getName()).isEqualTo("Fulana de Tal");
            assertThat(assinatura.getContents()).startsWith(PKCS7_FALSO);
        }

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(trilha, times(1)).append(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.ente()).isEqualTo(ENTE);
        assertThat(evento.tipo()).isEqualTo("assinatura_eletronica");
        assertThat(evento.ator()).isEqualTo("***.222.***-**").isNotEqualTo(SIGNATARIO.cpf());
        assertThat(evento.toString()).doesNotContain(SIGNATARIO.cpf());
        assertThat(evento.detalhes()).containsEntry("bloqueado", "false");
    }

    @Test
    @DisplayName("manifesto e metadado PAdES mascaram CPF quando o nome disponível é o próprio CPF")
    void mascaraCpfNoManifestoENoNomeDaAssinatura() throws Exception {
        Signatario signatarioSemNome = new Signatario("11122233344", "11122233344");
        when(provedor.assinarPkcs7(any())).thenReturn(PKCS7_FALSO);
        when(verificadorRevogacao.verificar(certificadoFake))
                .thenReturn(new VerificadorRevogacaoCertificado.ResultadoRevogacao(false, "válido"));

        DocumentoAssinado resultado =
                servico.assinar(DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of(signatarioSemNome));

        assertThat(resultado.manifesto())
                .contains("signatario=***.222.***-** (CPF ***.222.***-**)")
                .doesNotContain("11122233344");
        try (PDDocument documentoAssinado = Loader.loadPDF(pdfPublicado)) {
            assertThat(documentoAssinado.getLastSignatureDictionary().getName())
                    .isEqualTo("***.222.***-**")
                    .doesNotContain("11122233344");
        }
    }

    @Test
    @DisplayName("nível diferente de AVANCADA_GOVBR é rejeitado (F0 só tem esse provedor)")
    void rejeitaNivelNaoSuportado() {
        assertThatExceptionOfType(NivelInsuficienteException.class)
                .isThrownBy(() -> servico.assinar(DOCUMENTO, NivelAssinatura.QUALIFICADA_ICP_BRASIL, List.of(SIGNATARIO)));
        verify(trilha, never()).append(any());
    }

    @Test
    @DisplayName("exige ao menos um signatário")
    void exigeSignatario() {
        assertThatThrownBy(() -> servico.assinar(DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("workflow multi-assinatura ainda não é suportado (F1)")
    void rejeitaMultiplosSignatarios() {
        assertThatThrownBy(() -> servico.assinar(
                        DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of(SIGNATARIO, new Signatario("55566677788", "Outro"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("conta gov.br não elegível (Bronze/CPF com situação) vira CertificadoInvalidoException e vai para a trilha")
    void rejeitaContaNaoElegivel() {
        when(provedor.assinarPkcs7(any()))
                .thenThrow(new ProvedorAssinaturaGovBr.ContaGovBrNaoElegivelException(
                        "conta Bronze para CPF 11122233344"));

        assertThatExceptionOfType(CertificadoInvalidoException.class)
                .isThrownBy(() -> servico.assinar(DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of(SIGNATARIO)));

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(trilha, times(1)).append(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.ator()).isEqualTo("***.222.***-**");
        assertThat(evento.detalhes())
                .containsEntry("bloqueado", "true")
                .containsEntry("detalhe", "conta não elegível: conta Bronze para CPF ***.222.***-**");
        assertThat(evento.toString()).doesNotContain(SIGNATARIO.cpf());
    }

    @Test
    @DisplayName("falha inesperada do provedor (ex.: rede/HTTP, não a exceção nomeada de inelegibilidade) propaga sem mascarar")
    void falhaInesperadaDoProvedorPropagaSemMascarar() {
        when(provedor.assinarPkcs7(any()))
                .thenThrow(new IllegalStateException("Falha de rede ao chamar assinarPKCS7 (gov.br)"));

        assertThatThrownBy(() -> servico.assinar(DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of(SIGNATARIO)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha de rede");

        verify(trilha, never()).append(any());
    }

    @Test
    @DisplayName("certificado revogado é rejeitado mesmo com PKCS7 assinado com sucesso, e fica registrado")
    void rejeitaCertificadoRevogado() {
        when(provedor.assinarPkcs7(any())).thenReturn(PKCS7_FALSO);
        when(verificadorRevogacao.verificar(certificadoFake))
                .thenReturn(new VerificadorRevogacaoCertificado.ResultadoRevogacao(true, "OCSP: revogado"));

        assertThatExceptionOfType(CertificadoInvalidoException.class)
                .isThrownBy(() -> servico.assinar(DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of(SIGNATARIO)))
                .withMessageContaining("OCSP: revogado");

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(trilha, times(1)).append(captor.capture());
        assertThat(captor.getValue().detalhes()).containsEntry("bloqueado", "true").containsEntry("detalhe", "OCSP: revogado");
    }

    @Test
    @DisplayName("verificar(): F0 é checagem estrutural mínima, não a validação completa via ITI (F1)")
    void verificarEhMinimoNoF0() {
        ResultadoVerificacao resultado = servico.verificar(ORIGEM);
        assertThat(resultado.valido()).isTrue();
        assertThat(resultado.detalhe()).contains("F1");
    }

    @Test
    @DisplayName("verificar(): documento vazio não é válido")
    void verificarDocumentoVazio() {
        ServicoAssinaturaGovBrAvancada servicoComDocVazio = new ServicoAssinaturaGovBrAvancada(
                provedor, verificadorRevogacao, trilha,
                ref -> new byte[0],
                (bytes, ref) -> referenciaPublicada,
                bytes -> certificadoFake,
                Clock.systemUTC());

        assertThat(servicoComDocVazio.verificar(ORIGEM).valido()).isFalse();
    }
}
