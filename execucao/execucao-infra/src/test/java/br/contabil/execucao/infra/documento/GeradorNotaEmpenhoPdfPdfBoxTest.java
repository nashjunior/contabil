package br.contabil.execucao.infra.documento;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

class GeradorNotaEmpenhoPdfPdfBoxTest {

    private final GeradorNotaEmpenhoPdfPdfBox gerador = new GeradorNotaEmpenhoPdfPdfBox();

    @Test
    @DisplayName("PDF da nota de empenho mascara CPF do autor")
    void mascaraCpfDoAutorNoPdf() throws IOException {
        byte[] pdf = gerador.gerar(empenhoComAutor("12345678901"));

        String texto = extrairTexto(pdf);

        assertThat(texto).contains("Autor: ***.456.***-**");
        assertThat(texto).doesNotContain("12345678901");
    }

    private static Empenho empenhoComAutor(String cpf) {
        return Empenho.registrar(
                EmpenhoId.novo(),
                TenantId.de(UUID.randomUUID().toString()),
                1L,
                2026,
                TipoEmpenho.ORDINARIO,
                DotacaoId.novo(),
                CredorId.novo(),
                UnidadeGestoraId.novo(),
                null,
                Dinheiro.de("1000.00"),
                LocalDate.of(2026, 7, 20),
                "04.122.0001.2001",
                "0100000000",
                "empenho de material de expediente",
                new ReferenciaFatoContabil(UUID.randomUUID()),
                new Cpf(cpf));
    }

    private static String extrairTexto(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
