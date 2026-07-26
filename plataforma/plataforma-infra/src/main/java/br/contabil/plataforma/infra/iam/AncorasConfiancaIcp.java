package br.contabil.plataforma.infra.iam;

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

final class AncorasConfiancaIcp {

    private AncorasConfiancaIcp() {}

    static Set<TrustAnchor> carregar(Path caminhoBundlePem) {
        if (caminhoBundlePem == null || !Files.isRegularFile(caminhoBundlePem)) {
            throw new IllegalStateException(
                    "bundle de ancoras de confianca ICP-Brasil nao encontrado em: " + caminhoBundlePem
                            + " — configure siafic.iam.icp.trust-store-pem; ver "
                            + "docs/operacao/RAZ-24-runbook-icp-brasil-trust-anchors.md");
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
