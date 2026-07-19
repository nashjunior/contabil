package br.contabil.plataforma.infra.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ReferenciaDocumento;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import java.net.URI;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ObjectStoreSeamsAssinaturaConfigurationTest {

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
