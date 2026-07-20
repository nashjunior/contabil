package br.contabil.assinatura;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.DocumentoParaAssinar;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ReferenciaDocumento;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.infra.assinatura.ProvedorAssinaturaGovBr;
import br.contabil.plataforma.infra.assinatura.ProvedorAssinaturaGovBrHttp;
import br.contabil.plataforma.infra.assinatura.ServicoAssinaturaGovBrAvancada;
import br.contabil.plataforma.infra.assinatura.VerificadorRevogacaoCertificado;
import br.contabil.plataforma.infra.assinatura.VerificadorRevogacaoCertificadoPkix;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.cert.TrustAnchor;
import java.time.Clock;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composição final de {@link ServicoAssinaturaGovBrAvancada} (RAZ-24): junta os três
 * seams que até aqui só existiam como colaboradores injetados de teste — OAuth2/BFF
 * interativo do signatário ({@link AssinaturaGovBrOAuthConfiguration}, RAZ-31), object
 * store ({@code ObjectStoreSeamsAssinaturaConfiguration}, RAZ-32/41/45) e incorporação
 * PAdES (RAZ-34, dentro do próprio {@code ServicoAssinaturaGovBrAvancada}) — hoje todos
 * adapters reais no repo.
 *
 * <p>Desligado por padrão ({@code siafic.assinatura.govbr.enabled=true} liga).
 * Pré-requisito: {@code contabil.objectstore.enabled=true} também precisa estar ligado
 * — é de lá que vêm os seams {@code leitorDocumento}/{@code publicadorDocumentoAssinado}/
 * {@code validadorTenant} injetados aqui. Sem isso o contexto Spring falha ao subir por
 * dependência ausente (fail-fast na composição, nunca fail-open no fluxo de assinatura).
 *
 * <p>As âncoras de confiança ICP-Brasil não são embutidas no código — rotação de CA é
 * operação de infraestrutura, não deploy de app. Vêm de um bundle PEM apontado por
 * {@code siafic.assinatura.icp-brasil.trust-store-pem}; runbook de provisionamento em
 * {@code docs/operacao/RAZ-24-runbook-icp-brasil-trust-anchors.md}.
 */
@Configuration
@ConditionalOnProperty(prefix = "siafic.assinatura.govbr", name = "enabled", havingValue = "true")
class ServicoAssinaturaGovBrConfiguration {

    @Bean
    ProvedorAssinaturaGovBr provedorAssinaturaGovBr(
            HttpClient assinaturaGovBrHttpClient,
            Supplier<String> tokenAcessoAssinaturaGovBr,
            @Value("${siafic.assinatura.govbr.api.base-uri:https://assinatura-api.staging.iti.br}") String baseUriTexto) {
        return new ProvedorAssinaturaGovBrHttp(
                assinaturaGovBrHttpClient, URI.create(baseUriTexto), tokenAcessoAssinaturaGovBr);
    }

    @Bean
    VerificadorRevogacaoCertificado verificadorRevogacaoCertificado(
            @Value("${siafic.assinatura.icp-brasil.trust-store-pem}") String trustStorePemCaminho) {
        Set<TrustAnchor> ancoras = AncorasConfiancaIcpBrasil.carregar(Path.of(trustStorePemCaminho));
        return new VerificadorRevogacaoCertificadoPkix(ancoras);
    }

    @Bean
    ServicoAssinatura servicoAssinaturaGovBr(
            ProvedorAssinaturaGovBr provedor,
            VerificadorRevogacaoCertificado verificadorRevogacao,
            AuditoriaEscrita trilha,
            Function<ReferenciaDocumento, byte[]> leitorDocumento,
            BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado,
            Consumer<DocumentoParaAssinar> validadorTenant,
            Clock clock) {
        return new ServicoAssinaturaGovBrAvancada(
                provedor,
                verificadorRevogacao,
                trilha,
                leitorDocumento,
                publicadorDocumentoAssinado,
                validadorTenant,
                clock);
    }
}
