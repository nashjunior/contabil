package br.contabil.plataforma.infra.documento;

import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Binding do object store S3-compatível (ADR-0018). Desligado por padrão
 * ({@code contabil.objectstore.enabled=true} para ligar) — ligado quando a montagem
 * de {@code ServicoAssinaturaGovBrAvancada} existir (RAZ-24); o adaptador já é
 * exercitado por teste unitário com {@code S3Client} mockado.
 *
 * <p>{@code endpoint} vazio → AWS S3 (path-style off, credencial via cadeia
 * padrão/IAM). {@code endpoint} preenchido → MinIO on-prem (path-style on,
 * credencial estática access/secret). {@code kms-key-id} vazio → SSE-S3 (AES256).
 */
@Configuration
@ConditionalOnProperty(prefix = "contabil.objectstore", name = "enabled", havingValue = "true")
public class ObjectStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean(S3Client.class)
    S3Client objectStoreS3Client(
            @Value("${contabil.objectstore.region:us-east-1}") String region,
            @Value("${contabil.objectstore.endpoint:}") String endpoint,
            @Value("${contabil.objectstore.access-key:}") String accessKey,
            @Value("${contabil.objectstore.secret-key:}") String secretKey) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (!accessKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(ArmazenamentoDocumentos.class)
    ArmazenamentoDocumentos armazenamentoDocumentos(
            S3Client objectStoreS3Client,
            @Value("${contabil.objectstore.kms-key-id:}") String kmsKeyId) {
        return new S3ArmazenamentoDocumentos(objectStoreS3Client, kmsKeyId);
    }
}
