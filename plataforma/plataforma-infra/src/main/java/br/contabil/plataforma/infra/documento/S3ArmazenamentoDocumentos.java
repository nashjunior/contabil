package br.contabil.plataforma.infra.documento;

import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import java.net.URI;
import java.util.Objects;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Adaptador de {@link ArmazenamentoDocumentos} sobre object store S3-compatível
 * (AWS S3 em nuvem ou MinIO on-prem), conforme ADR-0018.
 *
 * <p>Interpreta a referência como {@code s3://{bucket}/{chave}} — bucket = autoridade
 * da URI, chave = path sem a barra inicial. O endpoint concreto (AWS vs. MinIO) é do
 * {@link S3Client} injetado, não da URI.
 *
 * <p><b>Cifragem em repouso:</b> toda gravação aplica server-side encryption —
 * SSE-KMS quando há {@code kmsKeyId}, senão SSE-S3 (AES256). O adaptador nunca grava
 * em claro.
 */
public final class S3ArmazenamentoDocumentos implements ArmazenamentoDocumentos {

    private final S3Client s3;
    private final String kmsKeyId;

    /**
     * @param s3 cliente S3 já configurado (endpoint/credenciais/HTTP client)
     * @param kmsKeyId chave KMS para SSE-KMS; {@code null}/vazio usa SSE-S3 (AES256)
     */
    public S3ArmazenamentoDocumentos(S3Client s3, String kmsKeyId) {
        this.s3 = Objects.requireNonNull(s3, "s3");
        this.kmsKeyId = (kmsKeyId == null || kmsKeyId.isBlank()) ? null : kmsKeyId;
    }

    @Override
    public byte[] ler(URI referencia) {
        Objects.requireNonNull(referencia, "referencia");
        GetObjectRequest requisicao = GetObjectRequest.builder()
                .bucket(bucket(referencia))
                .key(chave(referencia))
                .build();
        return s3.getObjectAsBytes(requisicao).asByteArray();
    }

    @Override
    public URI armazenar(byte[] conteudo, URI destino) {
        Objects.requireNonNull(conteudo, "conteudo");
        Objects.requireNonNull(destino, "destino");
        PutObjectRequest.Builder requisicao = PutObjectRequest.builder()
                .bucket(bucket(destino))
                .key(chave(destino));
        if (kmsKeyId != null) {
            requisicao.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(kmsKeyId);
        } else {
            requisicao.serverSideEncryption(ServerSideEncryption.AES256);
        }
        s3.putObject(requisicao.build(), RequestBody.fromBytes(conteudo));
        return destino;
    }

    private static String bucket(URI uri) {
        String bucket = uri.getAuthority();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("URI sem bucket (autoridade): " + uri);
        }
        return bucket;
    }

    private static String chave(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            throw new IllegalArgumentException("URI sem chave (path): " + uri);
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
