package br.contabil.plataforma.infra.documento;

import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import br.contabil.plataforma.domain.cofre.CofreSegredos;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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
 * ({@code contabil.objectstore.enabled=true} para ligar) — pré-requisito de
 * {@code ServicoAssinaturaGovBrConfiguration} (RAZ-24), que consome os seams de
 * {@code ObjectStoreSeamsAssinaturaConfiguration} montados sobre este binding; o
 * adaptador já é exercitado por teste unitário com {@code S3Client} mockado.
 *
 * <p>{@code endpoint} vazio → AWS S3 (path-style off, credencial via cadeia
 * padrão/IAM). {@code endpoint} preenchido → MinIO on-prem (path-style on,
 * credencial estática resolvida do Cofre). {@code kms-key-id} vazio → SSE-S3 (AES256).
 */
@Configuration
@ConditionalOnProperty(prefix = "contabil.objectstore", name = "enabled", havingValue = "true")
public class ObjectStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean(S3Client.class)
    S3Client objectStoreS3Client(
            CofreSegredos cofreSegredos,
            @Value("${contabil.objectstore.region:us-east-1}") String region,
            @Value("${contabil.objectstore.endpoint:}") String endpoint,
            @Value("${contabil.objectstore.access-key-ref:}") String accessKeyRef,
            @Value("${contabil.objectstore.secret-key-ref:}") String secretKeyRef,
            @Value("${contabil.objectstore.cofre.conta-servico:siafic-objectstore}") String contaServico,
            @Value("${contabil.objectstore.cofre.escopos:cofre://siafic/f0/objectstore}") String escopos) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (!accessKeyRef.isBlank() || !secretKeyRef.isBlank()) {
            if (accessKeyRef.isBlank() || secretKeyRef.isBlank()) {
                throw new IllegalStateException("contabil.objectstore.access-key-ref e secret-key-ref devem ser configuradas juntas");
            }
            CofreSegredos.ContaServico conta = new CofreSegredos.ContaServico(contaServico, escopos(escopos));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    resolver(cofreSegredos, conta, accessKeyRef),
                    resolver(cofreSegredos, conta, secretKeyRef))));
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

    private static String resolver(
            CofreSegredos cofreSegredos, CofreSegredos.ContaServico conta, String referencia) {
        char[] valor = cofreSegredos
                .obter(conta, new CofreSegredos.ReferenciaSegredo(referencia))
                .valor();
        try {
            return new String(valor);
        } finally {
            Arrays.fill(valor, '\0');
        }
    }

    private static Set<String> escopos(String escopos) {
        return Arrays.stream(escopos.split(","))
                .map(String::trim)
                .filter(escopo -> !escopo.isBlank())
                .collect(Collectors.toSet());
    }
}
