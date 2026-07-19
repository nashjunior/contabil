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
import java.net.URI;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ServicoAssinaturaGovBrAvancadaTest {

    private static final ReferenciaDocumento ORIGEM = new ReferenciaDocumento(URI.create("s3://ged/empenho-1.pdf"));
    private static final TenantId ENTE = new TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final DocumentoParaAssinar DOCUMENTO = new DocumentoParaAssinar(ENTE, ORIGEM, "empenho");
    private static final Signatario SIGNATARIO = new Signatario("11122233344", "Fulana de Tal");
    private static final byte[] CONTEUDO = "conteudo do pdf".getBytes();
    private static final byte[] PKCS7_FALSO = "pkcs7-fake-bytes".getBytes();

    private ProvedorAssinaturaGovBr provedor;
    private VerificadorRevogacaoCertificado verificadorRevogacao;
    private AuditoriaEscrita trilha;
    private X509Certificate certificadoFake;
    private ServicoAssinaturaGovBrAvancada servico;
    private ReferenciaDocumento referenciaPublicada;

    @BeforeEach
    void montaServico() {
        provedor = mock(ProvedorAssinaturaGovBr.class);
        verificadorRevogacao = mock(VerificadorRevogacaoCertificado.class);
        trilha = mock(AuditoriaEscrita.class);
        certificadoFake = mock(X509Certificate.class);
        referenciaPublicada = new ReferenciaDocumento(URI.create("s3://ged/empenho-1-assinado.pdf"));

        when(trilha.append(any())).thenReturn(new AuditoriaEscrita.EntradaTrilha(UUID.randomUUID(), "hash", null, 1));

        servico = new ServicoAssinaturaGovBrAvancada(
                provedor,
                verificadorRevogacao,
                trilha,
                ref -> CONTEUDO,
                (bytes, ref) -> referenciaPublicada,
                doc -> ENTE,
                bytes -> certificadoFake,
                Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("caminho feliz: assina, checa revogação, registra na trilha e devolve o manifesto")
    void assinaComSucesso() throws Exception {
        when(provedor.assinarPkcs7(any())).thenReturn(PKCS7_FALSO);
        when(verificadorRevogacao.verificar(certificadoFake))
                .thenReturn(new VerificadorRevogacaoCertificado.ResultadoRevogacao(false, "válido"));

        DocumentoAssinado resultado = servico.assinar(DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of(SIGNATARIO));

        String hashEsperado = java.util.Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(CONTEUDO));
        assertThat(resultado.hash()).isEqualTo(hashEsperado);
        assertThat(resultado.pdfAssinado()).isEqualTo(referenciaPublicada);
        assertThat(resultado.manifesto()).contains("Fulana de Tal").contains("11122233344").contains("empenho");
        assertThat(resultado.idTransacao()).isNotNull();

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(trilha, times(1)).append(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.ente()).isEqualTo(ENTE);
        assertThat(evento.tipo()).isEqualTo("assinatura_eletronica");
        assertThat(evento.ator()).isEqualTo(SIGNATARIO.cpf());
        assertThat(evento.detalhes()).containsEntry("bloqueado", "false");
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
                .thenThrow(new ProvedorAssinaturaGovBr.ContaGovBrNaoElegivelException("conta Bronze"));

        assertThatExceptionOfType(CertificadoInvalidoException.class)
                .isThrownBy(() -> servico.assinar(DOCUMENTO, NivelAssinatura.AVANCADA_GOVBR, List.of(SIGNATARIO)));

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(trilha, times(1)).append(captor.capture());
        assertThat(captor.getValue().detalhes()).containsEntry("bloqueado", "true");
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
                doc -> ENTE,
                bytes -> certificadoFake,
                Clock.systemUTC());

        assertThat(servicoComDocVazio.verificar(ORIGEM).valido()).isFalse();
    }
}
