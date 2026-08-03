package br.contabil.prestacaocontas.infra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.prestacaocontas.application.RemessaSimTceCePort;
import br.contabil.prestacaocontas.domain.CampoLayoutRemessaSimTceCe;
import br.contabil.prestacaocontas.domain.DimensaoOrganizacionalSimTceCe;
import br.contabil.prestacaocontas.domain.LayoutRemessaSimTceCe;
import br.contabil.prestacaocontas.domain.RemessaSimTceCe;
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.LinhaBalancete;

/** Serializa a tabela 308 do SIM como PGI ASCII delimitado por vírgula e ZIP. */
public final class RemessaSimTceCeArquivoAdapter implements RemessaSimTceCePort {

    private static final String QUEBRA_LINHA = "\r\n";

    private final LayoutRemessaSimTceCe layoutTabela308;

    public RemessaSimTceCeArquivoAdapter(LayoutRemessaSimTceCe layoutTabela308) {
        this.layoutTabela308 = Objects.requireNonNull(layoutTabela308, "layoutTabela308");
    }

    @Override
    public RemessaSimTceCe gerarTabela308(Balancete balancete, List<DimensaoOrganizacionalSimTceCe> dimensoes) {
        Objects.requireNonNull(balancete, "balancete");
        Objects.requireNonNull(dimensoes, "dimensoes");
        List<DimensaoOrganizacionalSimTceCe> dimensoesNormalizadas = List.copyOf(dimensoes);
        if (dimensoesNormalizadas.isEmpty()) {
            throw new IllegalArgumentException("remessa SIM exige ao menos uma dimensão UO/UPC/UG");
        }

        String nomePgi = layoutTabela308.nomeArquivo(balancete.exercicio(), balancete.mes());
        byte[] conteudoPgi = conteudoPgi(balancete, dimensoesNormalizadas);
        byte[] conteudoZip = zip(nomePgi, conteudoPgi);

        return new RemessaSimTceCe(
                balancete.enteId(),
                balancete.exercicio(),
                balancete.mes(),
                layoutTabela308.tabela(),
                nomePgi,
                conteudoPgi,
                layoutTabela308.nomeZip(balancete.exercicio(), balancete.mes()),
                conteudoZip);
    }

    private byte[] conteudoPgi(Balancete balancete, List<DimensaoOrganizacionalSimTceCe> dimensoes) {
        StringBuilder linhas = new StringBuilder();
        for (DimensaoOrganizacionalSimTceCe dimensao : dimensoes) {
            for (LinhaBalancete linha : balancete.linhas()) {
                linhas.append(linhaPgi(balancete, dimensao, linha)).append(QUEBRA_LINHA);
            }
        }
        return linhas.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private String linhaPgi(Balancete balancete, DimensaoOrganizacionalSimTceCe dimensao, LinhaBalancete linha) {
        Map<String, String> valores = valores(balancete, dimensao, linha);
        StringJoiner joiner = new StringJoiner(layoutTabela308.delimitador());
        for (CampoLayoutRemessaSimTceCe campo : layoutTabela308.campos()) {
            String valor = campo.valorFixo().orElseGet(() -> resolver(valores, campo));
            joiner.add(campoCsvAscii(valor));
        }
        return joiner.toString();
    }

    private String resolver(Map<String, String> valores, CampoLayoutRemessaSimTceCe campo) {
        String origem = campo.origem().orElseThrow();
        if (!valores.containsKey(origem)) {
            throw new IllegalArgumentException(
                    "origem '%s' não suportada no campo SIM '%s'".formatted(origem, campo.nome()));
        }
        return valores.get(origem);
    }

    private Map<String, String> valores(
            Balancete balancete, DimensaoOrganizacionalSimTceCe dimensao, LinhaBalancete linha) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("tabela.codigo", layoutTabela308.tabela());
        valores.put("competencia.exercicio", Integer.toString(balancete.exercicio()));
        valores.put("competencia.mes", "%02d".formatted(balancete.mes()));
        valores.put("competencia.aaaamm", "%d%02d".formatted(balancete.exercicio(), balancete.mes()));
        valores.put("uo.codigo", dimensao.unidadeOrcamentaria());
        valores.put("upc.codigo", dimensao.unidadePrestacaoContas());
        valores.put("ug.codigo", dimensao.unidadeGestora());
        valores.put("conta.codigo", linha.codigo());
        valores.put("conta.descricao", linha.descricao());
        valores.put("conta.natureza_saldo", linha.naturezaSaldo());
        valores.put("saldo.anterior", dinheiro(linha.saldoAnterior()));
        valores.put("movimento.debito", dinheiro(linha.movimentoDebito()));
        valores.put("movimento.credito", dinheiro(linha.movimentoCredito()));
        valores.put("saldo.atual", dinheiro(linha.saldoAtual()));
        return valores;
    }

    private static String dinheiro(Dinheiro valor) {
        BigDecimal decimal = valor.valor();
        return decimal.toPlainString();
    }

    private static String campoCsvAscii(String valor) {
        String ascii = ascii(valor);
        if (ascii.contains(",") || ascii.contains("\"") || ascii.contains("\n") || ascii.contains("\r")) {
            return "\"" + ascii.replace("\"", "\"\"") + "\"";
        }
        return ascii;
    }

    private static String ascii(String valor) {
        String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalizado.replaceAll("[^\\x00-\\x7F]", "");
    }

    private static byte[] zip(String nomeEntrada, byte[] conteudo) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.US_ASCII)) {
                ZipEntry entrada = new ZipEntry(nomeEntrada);
                entrada.setTime(0L);
                zip.putNextEntry(entrada);
                zip.write(conteudo);
                zip.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("falha ao empacotar remessa SIM", ex);
        }
    }
}
