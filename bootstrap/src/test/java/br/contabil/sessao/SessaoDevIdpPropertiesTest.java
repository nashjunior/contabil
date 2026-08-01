package br.contabil.sessao;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class SessaoDevIdpPropertiesTest {

    private static final URI ISSUER = URI.create("https://e2e.siafic.local/idp");

    @Test
    void exigirConfiguracaoCompletaAceitaQuandoTodosOsCamposEstaoPresentes() {
        SessaoDevIdpProperties properties =
                new SessaoDevIdpProperties(true, ISSUER, "siafic-e2e", "chave-publica-pem", "chave-privada-pem");

        properties.exigirConfiguracaoCompleta();
    }

    @Test
    void exigirConfiguracaoCompletaRecusaIssuerAusente() {
        SessaoDevIdpProperties properties =
                new SessaoDevIdpProperties(true, null, "siafic-e2e", "chave-publica-pem", "chave-privada-pem");

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void exigirConfiguracaoCompletaRecusaAudienceEmBranco() {
        SessaoDevIdpProperties properties =
                new SessaoDevIdpProperties(true, ISSUER, " ", "chave-publica-pem", "chave-privada-pem");

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void exigirConfiguracaoCompletaRecusaChavePublicaEmBranco() {
        SessaoDevIdpProperties properties = new SessaoDevIdpProperties(true, ISSUER, "siafic-e2e", "", "chave-privada-pem");

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public-key-pem");
    }

    @Test
    void exigirConfiguracaoCompletaRecusaChavePrivadaEmBranco() {
        SessaoDevIdpProperties properties = new SessaoDevIdpProperties(true, ISSUER, "siafic-e2e", "chave-publica-pem", "");

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private-key-pem");
    }

    @Test
    void compactConstructorNormalizaCamposDeTextoNulosEComEspacos() {
        SessaoDevIdpProperties properties = new SessaoDevIdpProperties(false, null, null, "  chave-publica  ", null);

        assertThat(properties.audience()).isEmpty();
        assertThat(properties.publicKeyPem()).isEqualTo("chave-publica");
        assertThat(properties.privateKeyPem()).isEmpty();
    }
}
