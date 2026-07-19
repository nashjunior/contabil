package br.contabil.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardrail RAZ-37 (13-nfr §piso "proibição de PII real em não-produção"; AC3/AC4): varre
 * fixtures/seeds/testes do repositório atrás de PII com <b>aparência real</b> — CPF com
 * checksum válido, endereço com CEP formal junto de logradouro, dado bancário em formato
 * IBAN com checksum válido — e falha o build se achar. Complementa (não substitui) o
 * gitleaks (.gitleaks.toml/RAZ-16), que cobre segredo, não dado pessoal.
 *
 * <p>Varredura léxica sobre o TEXTO dos arquivos (não bytecode) — roda sem Docker, junto dos
 * demais guardrails deste módulo em {@code ./gradlew check}. Escopo: qualquer arquivo de
 * texto sob {@code src/test/} de qualquer módulo, mais qualquer caminho contendo
 * {@code seed}/{@code fixture} fora dessa árvore.
 *
 * <p><b>Por que checksum, não "parece CPF"</b>: CPF sintético de teste convencional neste
 * repo (ex.: {@code ServicoAssinaturaGovBrAvancadaTest}) já é checksum-inválido por
 * construção — exigir checksum válido para disparar evita falso positivo em fixture
 * legítima e ainda pega o caso que importa (alguém colou um CPF real).
 *
 * <p>O relatório de violação NUNCA imprime o valor casado (só arquivo/linha/tipo) — o
 * próprio guardrail não pode virar vazamento de PII no log do CI.
 */
class SemPiiRealEmFixturesTest {

    private static final Set<String> DIRETORIOS_IGNORADOS =
            Set.of(".git", "build", "bin", ".gradle", ".idea", "node_modules");
    private static final Set<String> EXTENSOES_ESCANEADAS =
            Set.of("java", "sql", "yml", "yaml", "json", "properties", "csv", "txt");

    // Explícita, por valor — nunca por caminho (lição do .gitleaks.toml: excluir por path cega
    // a varredura da árvore inteira). Vazia hoje; se algum dia uma fixture didática precisar de
    // um CPF checksum-válido conhecido, ela entra aqui, um por um, com justificativa.
    private static final Set<String> CPFS_SINTETICOS_PERMITIDOS = Set.of();

    private static final Pattern CPF_FORMATADO = Pattern.compile("(?<!\\d)\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}(?!\\d)");
    private static final Pattern CPF_CRU = Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");
    private static final Pattern CEP = Pattern.compile("(?<!\\d)\\d{5}-?\\d{3}(?!\\d)");
    private static final Pattern LOGRADOURO =
            Pattern.compile("(?i)\\b(rua|av\\.?|avenida|alameda|travessa|rodovia|pra[cç]a)\\b");
    private static final Pattern IBAN_CANDIDATO = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b");

    @Test
    @DisplayName("nenhuma fixture/seed/teste do repositório contém PII real óbvia (13-nfr §piso)")
    void nenhumaFixtureDeTesteContemPiiRealObvia() throws IOException {
        Path raiz = raizDoRepositorio();

        List<Violacao> violacoes = escanearArvore(raiz);

        assertThat(violacoes)
                .as(
                        "PII com aparência real (CPF/endereço/dado bancário com checksum válido) não pode "
                                + "entrar em fixture/seed/teste — 13-nfr §piso 'sem PII em não-produção'. "
                                + "Achados (sem o valor, só onde): %s",
                        violacoes)
                .isEmpty();
    }

    @Test
    @DisplayName("detecta CPF com checksum válido e ignora CPF sintético (checksum inválido)")
    void detectaSomenteCpfComChecksumValido() {
        // Os dígitos verificadores são CALCULADOS em runtime (cpfComDigitosVerificadoresCalculados)
        // — nenhum CPF de 11 dígitos com DV correto fica escrito no texto-fonte deste arquivo,
        // nem mesmo particionado: o próprio princípio do guardrail é "checksum válido = suspeito",
        // então o teste não pode depender de um CPF-exemplo pronto (ainda que didático conhecido).
        String cpfComChecksumValido = cpfComDigitosVerificadoresCalculados("987654321");
        String cpfSemChecksumValido = "111222333" + "44"; // mesmo padrão já usado no repo (RAZ-11)

        List<Violacao> comValido = escanearLinhas("inline-test", List.of("cpf: " + cpfComChecksumValido));
        List<Violacao> comInvalido = escanearLinhas("inline-test", List.of("cpf: " + cpfSemChecksumValido));

        assertThat(comValido).extracting(Violacao::tipo).containsExactly("cpf_real_suspeito");
        assertThat(comInvalido).isEmpty();
    }

    @Test
    @DisplayName("CPF com todos os dígitos iguais nunca é válido (regra do próprio algoritmo)")
    void cpfComDigitosRepetidosNuncaEValido() {
        List<Violacao> violacoes = escanearLinhas("inline-test", List.of("cpf: " + "111111111" + "11"));

        assertThat(violacoes).isEmpty();
    }

    @Test
    @DisplayName("detecta endereço com CEP formal + logradouro na mesma linha; ignora sem CEP")
    void detectaEnderecoComCepELogradouro() {
        // CEP dividido em duas literais pelo mesmo motivo do teste de CPF acima.
        String cep = "01310" + "-100";

        List<Violacao> comCep = escanearLinhas("inline-test", List.of("Rua Teste, 100 - CEP " + cep));
        List<Violacao> semCep = escanearLinhas("inline-test", List.of("Rua Teste, 100, bairro Centro"));

        assertThat(comCep).extracting(Violacao::tipo).containsExactly("endereco_real_suspeito");
        assertThat(semCep).isEmpty();
    }

    @Test
    @DisplayName("detecta dado bancário em formato IBAN com checksum válido")
    void detectaIbanComChecksumValido() {
        // Exemplo canônico de IBAN (Deutsche Bundesbank/ISO 13616), o mesmo usado em toda
        // literatura de validação — dividido em duas literais pelo mesmo motivo acima.
        String ibanValido = "DE89370400440532013" + "000";
        String naoEIban = "isso nao e um iban valido, so texto qualquer em maiusculas AB1234567890123";

        List<Violacao> comIban = escanearLinhas("inline-test", List.of("conta: " + ibanValido));
        List<Violacao> semIban = escanearLinhas("inline-test", List.of(naoEIban));

        assertThat(comIban).extracting(Violacao::tipo).containsExactly("dado_bancario_real_suspeito");
        assertThat(semIban).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // Varredura
    // ---------------------------------------------------------------------------

    private static Path raizDoRepositorio() {
        Path atual = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && atual != null; i++) {
            if (Files.exists(atual.resolve("settings.gradle.kts"))) {
                return atual;
            }
            atual = atual.getParent();
        }
        throw new IllegalStateException(
                "Não encontrei a raiz do repositório (settings.gradle.kts) a partir de "
                        + Path.of("").toAbsolutePath());
    }

    private static List<Violacao> escanearArvore(Path raiz) throws IOException {
        List<Path> candidatos;
        try (Stream<Path> caminhos = Files.walk(raiz)) {
            candidatos = caminhos
                    .filter(Files::isRegularFile)
                    .filter(SemPiiRealEmFixturesTest::foraDeDiretorioIgnorado)
                    .filter(SemPiiRealEmFixturesTest::eAlvoDeVarredura)
                    .toList();
        }

        List<Violacao> violacoes = new ArrayList<>();
        for (Path caminho : candidatos) {
            List<String> linhas = Files.readAllLines(caminho, StandardCharsets.UTF_8);
            violacoes.addAll(escanearLinhas(raiz.relativize(caminho).toString(), linhas));
        }
        return violacoes;
    }

    private static boolean foraDeDiretorioIgnorado(Path caminho) {
        String normalizado = "/" + caminho.toString().replace('\\', '/') + "/";
        return DIRETORIOS_IGNORADOS.stream().noneMatch(dir -> normalizado.contains("/" + dir + "/"));
    }

    private static boolean eAlvoDeVarredura(Path caminho) {
        String nome = caminho.getFileName().toString();
        int ponto = nome.lastIndexOf('.');
        String extensao = ponto >= 0 ? nome.substring(ponto + 1).toLowerCase(Locale.ROOT) : "";
        if (!EXTENSOES_ESCANEADAS.contains(extensao)) {
            return false;
        }
        String caminhoNormalizado = caminho.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return caminhoNormalizado.contains("/src/test/")
                || caminhoNormalizado.contains("seed")
                || caminhoNormalizado.contains("fixture");
    }

    static List<Violacao> escanearLinhas(String arquivo, List<String> linhas) {
        List<Violacao> violacoes = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            String linha = linhas.get(i);
            int numeroLinha = i + 1;

            boolean temCpfReal = encontrarTodos(CPF_FORMATADO, linha).stream().anyMatch(SemPiiRealEmFixturesTest::cpfValidoENaoPermitido)
                    || encontrarTodos(CPF_CRU, linha).stream().anyMatch(SemPiiRealEmFixturesTest::cpfValidoENaoPermitido);
            if (temCpfReal) {
                violacoes.add(new Violacao(arquivo, numeroLinha, "cpf_real_suspeito"));
            }

            if (LOGRADOURO.matcher(linha).find() && !encontrarTodos(CEP, linha).isEmpty()) {
                violacoes.add(new Violacao(arquivo, numeroLinha, "endereco_real_suspeito"));
            }

            boolean temIbanReal = encontrarTodos(IBAN_CANDIDATO, linha).stream().anyMatch(SemPiiRealEmFixturesTest::ibanValido);
            if (temIbanReal) {
                violacoes.add(new Violacao(arquivo, numeroLinha, "dado_bancario_real_suspeito"));
            }
        }
        return violacoes;
    }

    private static List<String> encontrarTodos(Pattern padrao, String linha) {
        List<String> achados = new ArrayList<>();
        Matcher m = padrao.matcher(linha);
        while (m.find()) {
            achados.add(m.group());
        }
        return achados;
    }

    // ---------------------------------------------------------------------------
    // Validação de checksum — só o que passa vira violação (evita falso positivo)
    // ---------------------------------------------------------------------------

    private static boolean cpfValidoENaoPermitido(String candidato) {
        String digitos = somenteDigitos(candidato);
        return cpfValido(digitos) && !CPFS_SINTETICOS_PERMITIDOS.contains(digitos);
    }

    private static boolean cpfValido(String digitos) {
        if (digitos.length() != 11) {
            return false;
        }
        if (digitos.chars().distinct().count() == 1) {
            return false; // 000.000.000-00 .. 999.999.999-99: formato válido, CPF nunca válido
        }
        int[] d = digitos.chars().map(c -> c - '0').toArray();

        int soma1 = 0;
        for (int i = 0; i < 9; i++) {
            soma1 += d[i] * (10 - i);
        }
        int resto1 = soma1 % 11;
        int dv1 = resto1 < 2 ? 0 : 11 - resto1;
        if (dv1 != d[9]) {
            return false;
        }

        int soma2 = 0;
        for (int i = 0; i < 10; i++) {
            soma2 += d[i] * (11 - i);
        }
        int resto2 = soma2 % 11;
        int dv2 = resto2 < 2 ? 0 : 11 - resto2;
        return dv2 == d[10];
    }

    private static String somenteDigitos(String valor) {
        return valor.replaceAll("[^0-9]", "");
    }

    /**
     * Calcula os 2 dígitos verificadores de um CPF a partir dos 9 dígitos-base — usado só pelo
     * teste {@code detectaSomenteCpfComChecksumValido} para gerar em runtime um CPF com checksum
     * válido, sem escrever nenhum CPF pronto no texto-fonte deste arquivo (nem didático).
     */
    private static String cpfComDigitosVerificadoresCalculados(String noveDigitosBase) {
        int[] base = noveDigitosBase.chars().map(c -> c - '0').toArray();
        int dv1 = dvCpf(base, 10);
        int[] comDv1 = java.util.Arrays.copyOf(base, base.length + 1);
        comDv1[9] = dv1;
        int dv2 = dvCpf(comDv1, 11);
        return noveDigitosBase + dv1 + dv2;
    }

    private static int dvCpf(int[] digitos, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < digitos.length; i++) {
            soma += digitos[i] * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** IBAN (ISO 13616): rearranja BBAN+país+dv para o fim, letras viram A=10..Z=35, valida mod 97 == 1. */
    private static boolean ibanValido(String candidato) {
        if (candidato.length() < 15 || candidato.length() > 34) {
            return false;
        }
        String rearranjado = candidato.substring(4) + candidato.substring(0, 4);
        StringBuilder numerico = new StringBuilder();
        for (int i = 0; i < rearranjado.length(); i++) {
            char c = rearranjado.charAt(i);
            if (Character.isDigit(c)) {
                numerico.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                numerico.append(c - 'A' + 10);
            } else {
                return false;
            }
        }
        return new BigInteger(numerico.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    private record Violacao(String arquivo, int linha, String tipo) {
        @Override
        public String toString() {
            return arquivo + ":" + linha + " [" + tipo + "]";
        }
    }
}
