package br.contabil.plataforma.infra.assinatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.DocumentoParaAssinar;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ReferenciaDocumento;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoTenantInvalidoException;
import java.net.URI;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ObjectStoreSeamsAssinaturaConfigurationTest {

    private static final TenantId ENTE = new TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final URI URI_COM_PREFIXO =
            URI.create("s3://ged/11111111-1111-1111-1111-111111111111/empenho-1.pdf");
    private static final URI URI_SEM_PREFIXO = URI.create("s3://ged/empenho-1.pdf");

    private final ObjectStoreSeamsAssinaturaConfiguration config = new ObjectStoreSeamsAssinaturaConfiguration();

    @Test
    void uri_assinada_insere_sufixo_antes_da_extensao() {
        assertThat(ObjectStoreSeamsAssinaturaConfiguration.uriAssinada(URI.create("s3://ged/empenho-1.pdf")))
                .isEqualTo(URI.create("s3://ged/empenho-1-assinado.pdf"));
        assertThat(ObjectStoreSeamsAssinaturaConfiguration.uriAssinada(URI.create("s3://ged/ente/x.pdf")))
                .isEqualTo(URI.create("s3://ged/ente/x-assinado.pdf"));
        assertThat(ObjectStoreSeamsAssinaturaConfiguration.uriAssinada(URI.create("s3://ged/sem-extensao")))
                .isEqualTo(URI.create("s3://ged/sem-extensao-assinado"));
    }

    @Test
    void leitor_delega_ao_armazenamento_pela_uri() {
        var armazenamento = new ArmazenamentoStub();
        Function<ReferenciaDocumento, byte[]> leitor = config.leitorDocumento(armazenamento);

        byte[] lido = leitor.apply(new ReferenciaDocumento(URI.create("s3://ged/empenho-1.pdf")));

        assertThat(new String(lido)).isEqualTo("conteudo:s3://ged/empenho-1.pdf");
    }

    @Test
    void publicador_grava_na_uri_assinada_e_devolve_a_referencia() {
        var armazenamento = new ArmazenamentoStub();
        BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicador =
                config.publicadorDocumentoAssinado(armazenamento);

        ReferenciaDocumento nova = publicador.apply(
                "assinado".getBytes(), new ReferenciaDocumento(URI.create("s3://ged/empenho-1.pdf")));

        assertThat(nova.uri()).isEqualTo(URI.create("s3://ged/empenho-1-assinado.pdf"));
        assertThat(armazenamento.ultimoDestino).isEqualTo(URI.create("s3://ged/empenho-1-assinado.pdf"));
    }

    @Test
    void validador_tenant_aceita_uri_com_prefixo_correto() {
        Consumer<DocumentoParaAssinar> validador = config.validadorTenant();
        DocumentoParaAssinar doc = new DocumentoParaAssinar(
                ENTE, new ReferenciaDocumento(URI_COM_PREFIXO), "empenho");

        assertThatCode(() -> validador.accept(doc)).doesNotThrowAnyException();
    }

    @Test
    void validador_tenant_rejeita_uri_sem_prefixo_do_ente() {
        Consumer<DocumentoParaAssinar> validador = config.validadorTenant();
        DocumentoParaAssinar doc = new DocumentoParaAssinar(
                ENTE, new ReferenciaDocumento(URI_SEM_PREFIXO), "empenho");

        assertThatThrownBy(() -> validador.accept(doc))
                .isInstanceOf(DocumentoTenantInvalidoException.class)
                .hasMessageContaining("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void validar_prefixo_rejeita_uuid_de_outro_ente() {
        TenantId outroEnte = new TenantId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        assertThatThrownBy(() ->
                ObjectStoreSeamsAssinaturaConfiguration.validarPrefixoTenant(outroEnte, URI_COM_PREFIXO))
                .isInstanceOf(DocumentoTenantInvalidoException.class)
                .hasMessageContaining("22222222-2222-2222-2222-222222222222");
    }

    private static final class ArmazenamentoStub implements ArmazenamentoDocumentos {
        private URI ultimoDestino;

        @Override
        public byte[] ler(URI referencia) {
            return ("conteudo:" + referencia).getBytes();
        }

        @Override
        public URI armazenar(byte[] conteudo, URI destino) {
            this.ultimoDestino = destino;
            return destino;
        }
    }
}
