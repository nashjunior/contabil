package br.contabil.assinatura;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Carrega o bundle de âncoras de confiança (CA raiz/intermediária ICP-Brasil) de um
 * arquivo PEM apontado por configuração externa — nunca embutido no código-fonte,
 * porque o bundle oficial muda por rotação de CA (é operação de infraestrutura, não
 * deploy de app). Consumido por {@code VerificadorRevogacaoCertificadoPkix} (RAZ-24).
 *
 * <p>Provisionamento do bundle: docs/operacao/RAZ-24-runbook-icp-brasil-trust-anchors.md.
 */
final class AncorasConfiancaIcpBrasil {

    private AncorasConfiancaIcpBrasil() {}

    static Set<TrustAnchor> carregar(Path caminhoBundlePem) {
        if (caminhoBundlePem == null || !Files.isRegularFile(caminhoBundlePem)) {
            throw new IllegalStateException(
                    "bundle de ancoras de confianca ICP-Brasil nao encontrado em: " + caminhoBundlePem
                            + " — ver docs/operacao/RAZ-24-runbook-icp-brasil-trust-anchors.md");
        }
        try (InputStream entrada = Files.newInputStream(caminhoBundlePem)) {
            CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
            Set<TrustAnchor> ancoras = new LinkedHashSet<>();
            for (var certificado : fabrica.generateCertificates(entrada)) {
                ancoras.add(new TrustAnchor((X509Certificate) certificado, null));
            }
            if (ancoras.isEmpty()) {
                throw new IllegalStateException(
                        "bundle de ancoras de confianca ICP-Brasil em " + caminhoBundlePem
                                + " nao contem nenhum certificado");
            }
            return ancoras;
        } catch (IOException | CertificateException e) {
            throw new IllegalStateException(
                    "falha ao carregar bundle de ancoras de confianca ICP-Brasil de " + caminhoBundlePem, e);
        }
    }
}
