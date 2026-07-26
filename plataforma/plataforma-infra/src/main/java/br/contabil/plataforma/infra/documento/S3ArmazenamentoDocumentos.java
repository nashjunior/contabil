package br.contabil.plataforma.infra.documento;

import java.net.URI;
import java.util.Objects;

import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
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
 *
 * <p><b>Append-only:</b> {@code armazenar} usa escrita condicional
 * ({@code If-None-Match: *}) — a gravação falha se já existir objeto na chave de
 * destino, em vez de sobrescrever silenciosamente (RAZ-45). Exige backend
 * S3-compatível com suporte a conditional writes (AWS S3 nativamente; MinIO em
 * versões recentes).
 */
public final class S3ArmazenamentoDocumentos implements ArmazenamentoDocumentos {

    private static final int HTTP_PRECONDITION_FAILED = 412;

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
        try {
            return s3.getObjectAsBytes(requisicao).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new DocumentoNaoEncontradoException("documento ausente na referência: " + referencia);
        }
    }

    @Override
    public URI armazenar(byte[] conteudo, URI destino) {
        Objects.requireNonNull(conteudo, "conteudo");
        Objects.requireNonNull(destino, "destino");
        PutObjectRequest.Builder requisicao = PutObjectRequest.builder()
                .bucket(bucket(destino))
                .key(chave(destino))
                .ifNoneMatch("*");
        if (kmsKeyId != null) {
            requisicao.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(kmsKeyId);
        } else {
            requisicao.serverSideEncryption(ServerSideEncryption.AES256);
        }
        try {
            s3.putObject(requisicao.build(), RequestBody.fromBytes(conteudo));
        } catch (S3Exception e) {
            if (e.statusCode() == HTTP_PRECONDITION_FAILED) {
                throw new DocumentoJaExistenteException(
                        "já existe documento na chave de destino, escrita rejeitada (append-only): " + destino);
            }
            throw e;
        }
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
