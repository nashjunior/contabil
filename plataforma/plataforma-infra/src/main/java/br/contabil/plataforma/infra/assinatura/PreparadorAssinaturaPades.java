package br.contabil.plataforma.infra.assinatura;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

/**
 * Prepara o placeholder de assinatura PAdES (dicionário {@code /Sig}, {@code /ByteRange};
 * ISO 32000-1 §12.8) num PDF já existente e calcula o hash SHA-256 sobre os bytes desse
 * byte-range — o hash que precisa virar o input de {@link ProvedorAssinaturaGovBr#assinarPkcs7}.
 *
 * <p><b>Ordem importa</b>: o CMS/PKCS#7 devolvido pelo provedor assina o hash do byte-range
 * do PDF <i>com o placeholder já reservado</i>, não o hash do documento bruto — se o hash
 * fosse calculado antes do placeholder existir, o {@code /ByteRange} do dicionário de
 * assinatura não bateria com o que foi realmente assinado, e a assinatura PAdES seria
 * inválida (rejeitada por qualquer validador, inclusive o do ITI). Por isso esta classe
 * expõe duas fases: {@link #preparar} (placeholder + hash) e, só depois de o provedor
 * assinar esse hash, {@link PreparoAssinaturaPades#embutir} (incremental save do CMS).
 *
 * <p>Usa Apache PDFBox ({@code saveIncrementalForExternalSigning}), que já resolve o
 * byte-range e a codificação hex do {@code /Contents} — não há cálculo manual de offsets
 * aqui.
 */
final class PreparadorAssinaturaPades {

    /**
     * Tamanho reservado para o {@code /Contents} do dicionário de assinatura, generoso o
     * bastante para um PKCS#7 gov.br real (cadeia de certificados ICP-Brasil/AC + CMS,
     * tipicamente bem acima do padrão do PDFBox de ~9,25 KiB) sem estourar o placeholder
     * no {@link PreparoAssinaturaPades#embutir}.
     */
    private static final int TAMANHO_RESERVADO_ASSINATURA = 32 * 1024;

    PreparoAssinaturaPades preparar(byte[] pdfOriginal, String nomeSignatario, Instant momento) {
        PDDocument documento;
        try {
            documento = Loader.loadPDF(pdfOriginal);
        } catch (IOException e) {
            throw new IllegalStateException("falha ao carregar PDF para preparo da assinatura PAdES", e);
        }
        try {
            PDSignature assinatura = new PDSignature();
            assinatura.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            assinatura.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            assinatura.setName(nomeSignatario);
            assinatura.setSignDate(calendarioDe(momento));

            SignatureOptions opcoes = new SignatureOptions();
            opcoes.setPreferredSignatureSize(TAMANHO_RESERVADO_ASSINATURA);
            documento.addSignature(assinatura, opcoes);

            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ExternalSigningSupport suporteExterno = documento.saveIncrementalForExternalSigning(saida);
            byte[] hash = sha256(suporteExterno.getContent().readAllBytes());

            return new PreparoAssinaturaPades(hash, documento, suporteExterno, saida);
        } catch (IOException e) {
            fecharSilenciosamente(documento);
            throw new IllegalStateException("falha ao reservar o placeholder de assinatura PAdES no PDF", e);
        }
    }

    /** Hash sobre o byte-range preparado (input de {@code assinarPkcs7}) + callback para embutir o CMS devolvido. */
    static final class PreparoAssinaturaPades {
        private final byte[] hashSha256;
        private final PDDocument documento;
        private final ExternalSigningSupport suporteExterno;
        private final ByteArrayOutputStream saida;

        private PreparoAssinaturaPades(
                byte[] hashSha256, PDDocument documento, ExternalSigningSupport suporteExterno, ByteArrayOutputStream saida) {
            this.hashSha256 = hashSha256;
            this.documento = documento;
            this.suporteExterno = suporteExterno;
            this.saida = saida;
        }

        byte[] hashSha256() {
            return hashSha256;
        }

        /** Descarta o preparo (fecha o {@code PDDocument} aberto) quando a assinatura não avança para a fase 2. */
        void descartar() {
            fecharSilenciosamente(documento);
        }

        /** Embute o CMS/PKCS#7 no placeholder reservado e devolve o PDF assinado completo (PAdES). */
        byte[] embutir(byte[] pkcs7) {
            try {
                suporteExterno.setSignature(pkcs7);
                return saida.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("falha ao embutir o CMS/PKCS#7 no placeholder PAdES reservado", e);
            } finally {
                fecharSilenciosamente(documento);
            }
        }
    }

    private static Calendar calendarioDe(Instant momento) {
        Calendar calendario = new GregorianCalendar();
        calendario.setTimeInMillis(momento.toEpochMilli());
        calendario.setTimeZone(java.util.TimeZone.getTimeZone(ZoneOffset.UTC));
        return calendario;
    }

    private static void fecharSilenciosamente(PDDocument documento) {
        try {
            documento.close();
        } catch (IOException ignorada) {
            // best-effort: o PDF já foi produzido (ou o preparo falhou) — não há o que fazer com o handle.
        }
    }

    private static byte[] sha256(byte[] conteudo) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(conteudo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
