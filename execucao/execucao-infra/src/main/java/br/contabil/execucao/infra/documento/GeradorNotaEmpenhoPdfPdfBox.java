package br.contabil.execucao.infra.documento;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.GeradorNotaEmpenhoPdf;

/**
 * Gera o conteúdo PDF da nota de empenho (ADR-0027 (b)/item 3) a partir do
 * agregado {@link Empenho}, com PDFBox (mesma biblioteca de {@code
 * PreparadorAssinaturaPades}/RAZ-34, aqui para o conteúdo do documento, não
 * para o placeholder de assinatura).
 *
 * <p>F0: layout textual mínimo com os campos do agregado — sem template
 * visual/timbre nem conformidade PDF/A-2b completa (metadados XMP, fontes
 * embutidas); o hash/assinatura/verificação PAdES que dão valor jurídico ao
 * documento (transversais/01-assinatura-eletronica.md) ficam por conta de
 * {@code ServicoAssinatura}, que consome a saída deste gerador.
 */
@Component
public class GeradorNotaEmpenhoPdfPdfBox implements GeradorNotaEmpenhoPdf {

    // int, não float: guardrail de build proíbe campo de ponto flutuante binário em
    // qualquer classe de produção (ADR-0006 é sobre DINHEIRO, mas a trava do
    // guardrail é ampla — não distingue por pacote); os métodos do PDFBox exigem
    // float só na CHAMADA (parâmetro/retorno, não campo), então o widening
    // int -> float acontece no local de uso, sem violar a regra.
    private static final int MARGEM = 60;
    private static final int TAMANHO_FONTE_TITULO = 16;
    private static final int TAMANHO_FONTE_CORPO = 11;
    private static final int ALTURA_LINHA = 16;

    @Override
    public byte[] gerar(Empenho empenho) {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDFont fonteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fonteCorpo = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                float y = pagina.getMediaBox().getHeight() - MARGEM;
                y = escreverLinha(
                        conteudo,
                        fonteTitulo,
                        TAMANHO_FONTE_TITULO,
                        y,
                        "Nota de Empenho nº %d/%d".formatted(empenho.numeroSequencial(), empenho.exercicio()));
                y -= ALTURA_LINHA;

                for (String linha : linhasDoConteudo(empenho)) {
                    y = escreverLinha(conteudo, fonteCorpo, TAMANHO_FONTE_CORPO, y, linha);
                }
            }

            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "falha ao gerar o PDF da nota de empenho " + empenho.id().valor(), e);
        }
    }

    private static float escreverLinha(
            PDPageContentStream conteudo, PDFont fonte, float tamanhoFonte, float y, String texto)
            throws IOException {
        conteudo.beginText();
        conteudo.setFont(fonte, tamanhoFonte);
        conteudo.newLineAtOffset(MARGEM, y);
        conteudo.showText(texto);
        conteudo.endText();
        return y - ALTURA_LINHA;
    }

    private static List<String> linhasDoConteudo(Empenho empenho) {
        return List.of(
                "Ente: %s".formatted(empenho.enteId().valor()),
                "Tipo: %s".formatted(empenho.tipo().codigo()),
                "Dotação: %s".formatted(empenho.dotacaoId().valor()),
                "Credor: %s".formatted(empenho.credorId().valor()),
                "Unidade gestora: %s".formatted(empenho.unidadeGestoraId().valor()),
                "Classificação orçamentária: %s".formatted(empenho.classificacaoOrcamentaria()),
                "Fonte de recurso: %s".formatted(empenho.fonteRecurso()),
                "Valor: R$ %s".formatted(empenho.valor().valor().toPlainString()),
                "Data do fato: %s".formatted(empenho.dataFato()),
                "Histórico: %s".formatted(empenho.historico()),
                "Autor: %s".formatted(empenho.autor().numero()));
    }
}
