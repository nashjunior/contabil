package br.contabil.plataforma.infra.assinatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.junit.jupiter.api.Test;

class PreparadorAssinaturaPadesTest {

    private static final byte[] PKCS7_FALSO = "pkcs7-fake-bytes-para-embutir-no-placeholder".getBytes();

    private final PreparadorAssinaturaPades preparador = new PreparadorAssinaturaPades();

    @Test
    void reservaPlaceholderEEmbuteCmsComByteRangeConsistenteComOHashAssinado() throws Exception {
        byte[] pdfOriginal = pdfMinimo();

        PreparadorAssinaturaPades.PreparoAssinaturaPades preparo =
                preparador.preparar(pdfOriginal, "Fulana de Tal", Instant.parse("2026-07-19T12:00:00Z"));

        assertThat(preparo.hashSha256()).hasSize(32);

        byte[] pdfAssinado = preparo.embutir(PKCS7_FALSO);

        try (PDDocument documentoAssinado = Loader.loadPDF(pdfAssinado)) {
            PDSignature assinatura = documentoAssinado.getLastSignatureDictionary();
            assertThat(assinatura).isNotNull();
            assertThat(assinatura.getFilter()).isEqualTo(PDSignature.FILTER_ADOBE_PPKLITE.getName());
            assertThat(assinatura.getSubFilter()).isEqualTo(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED.getName());
            assertThat(assinatura.getName()).isEqualTo("Fulana de Tal");
            assertThat(assinatura.getContents()).startsWith(PKCS7_FALSO);

            int[] byteRange = assinatura.getByteRange();
            assertThat(byteRange).hasSize(4);
            assertThat(byteRange[0]).isZero();

            // Prova a invariante central do PAdES: o hash que foi para assinarPkcs7 (fase 1)
            // precisa ser exatamente o SHA-256 dos bytes que o /ByteRange do dicionário
            // final (fase 2) delimita — senão o /ByteRange não bate com o que foi assinado.
            byte[] hashRecomputadoDoByteRangeFinal = sha256(bytesForaDoPlaceholder(pdfAssinado, byteRange));
            assertThat(hashRecomputadoDoByteRangeFinal).isEqualTo(preparo.hashSha256());
        }
    }

    @Test
    void descartarFechaOPddocumentoSemLancarQuandoAssinaturaNaoAvancaParaAFase2() {
        PreparadorAssinaturaPades.PreparoAssinaturaPades preparo =
                preparador.preparar(pdfMinimo(), "Fulana de Tal", Instant.parse("2026-07-19T12:00:00Z"));

        assertThatCode(preparo::descartar).doesNotThrowAnyException();
    }

    private static byte[] pdfMinimo() {
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage());
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("falha ao gerar PDF mínimo de teste", e);
        }
    }

    private static byte[] bytesForaDoPlaceholder(byte[] pdf, int[] byteRange) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(pdf, byteRange[0], byteRange[1]);
        saida.write(pdf, byteRange[2], byteRange[3]);
        return saida.toByteArray();
    }

    private static byte[] sha256(byte[] conteudo) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(conteudo);
    }
}
