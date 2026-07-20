package br.contabil.plataforma.infra.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoJaExistenteException;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoNaoEncontradoException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

class S3ArmazenamentoDocumentosTest {

    private static final URI DESTINO = URI.create("s3://ged/11111111-1111-1111-1111-111111111111/empenho-1-assinado.pdf");

    @Test
    void armazenar_extrai_bucket_e_chave_da_uri_e_aplica_sse_kms() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        var adaptador = new S3ArmazenamentoDocumentos(s3, "chave-kms-1");

        URI efetiva = adaptador.armazenar("conteudo".getBytes(StandardCharsets.UTF_8), DESTINO);

        var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo("ged");
        assertThat(req.key()).isEqualTo("11111111-1111-1111-1111-111111111111/empenho-1-assinado.pdf");
        assertThat(req.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
        assertThat(req.ssekmsKeyId()).isEqualTo("chave-kms-1");
        assertThat(req.ifNoneMatch()).isEqualTo("*");
        assertThat(efetiva).isEqualTo(DESTINO);
    }

    @Test
    void armazenar_e_condicional_e_nunca_sobrescreve_chave_existente() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(412).message("At least one of the pre-conditions you specified did not hold").build());
        var adaptador = new S3ArmazenamentoDocumentos(s3, null);

        assertThatThrownBy(() -> adaptador.armazenar("novo".getBytes(StandardCharsets.UTF_8), DESTINO))
                .isInstanceOf(DocumentoJaExistenteException.class);
    }

    @Test
    void armazenar_propaga_erro_s3_que_nao_e_precondicao_falha() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("erro interno").build());
        var adaptador = new S3ArmazenamentoDocumentos(s3, null);

        assertThatThrownBy(() -> adaptador.armazenar("novo".getBytes(StandardCharsets.UTF_8), DESTINO))
                .isInstanceOf(S3Exception.class)
                .isNotInstanceOf(DocumentoJaExistenteException.class);
    }

    @Test
    void armazenar_sem_kms_usa_sse_s3_aes256() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        var adaptador = new S3ArmazenamentoDocumentos(s3, "  ");

        adaptador.armazenar(new byte[] {1, 2, 3}, URI.create("s3://ged/m.json"));

        var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(captor.getValue().ssekmsKeyId()).isNull();
    }

    @Test
    void ler_busca_pelo_bucket_e_chave_da_uri() {
        S3Client s3 = mock(S3Client.class);
        byte[] armazenado = "documento-assinado".getBytes(StandardCharsets.UTF_8);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), armazenado));
        var adaptador = new S3ArmazenamentoDocumentos(s3, null);

        byte[] lido = adaptador.ler(URI.create("s3://ged/empenho-1.pdf"));

        var captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("ged");
        assertThat(captor.getValue().key()).isEqualTo("empenho-1.pdf");
        assertThat(lido).isEqualTo(armazenado);
    }

    @Test
    void uri_sem_chave_e_rejeitada() {
        var adaptador = new S3ArmazenamentoDocumentos(mock(S3Client.class), null);
        assertThatThrownBy(() -> adaptador.ler(URI.create("s3://ged")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ler_objeto_ausente_vira_documento_nao_encontrado() {
        S3Client s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("The specified key does not exist.").build());
        var adaptador = new S3ArmazenamentoDocumentos(s3, null);

        assertThatThrownBy(() -> adaptador.ler(URI.create("s3://ged/inexistente.pdf")))
                .isInstanceOf(DocumentoNaoEncontradoException.class);
    }
}
