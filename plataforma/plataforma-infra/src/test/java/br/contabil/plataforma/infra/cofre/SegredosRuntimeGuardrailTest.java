package br.contabil.plataforma.infra.cofre;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SegredosRuntimeGuardrailTest {

    private static final Pattern CHAVE_SENSIVEL_COM_DEFAULT =
            Pattern.compile("(?i)^\\s*(password|client-secret|secret-key|access-token|token|certificado|private-key)\\s*:\\s*\\$\\{[^}]+:");
    private static final Pattern VALOR_SENSIVEL_LITERAL =
            Pattern.compile("(?i)^\\s*(password|client-secret|secret-key|access-token|token|certificado|private-key)\\s*:\\s*[^$\\s#][^#]*$");

    @Test
    void applicationYmlNaoContemSegredoRuntimeNemFallbackHardcoded() throws IOException {
        Path application = Path.of("../../bootstrap/src/main/resources/application.yml");

        var violacoes = Files.readAllLines(application).stream()
                .filter(linha -> CHAVE_SENSIVEL_COM_DEFAULT.matcher(linha).find()
                        || VALOR_SENSIVEL_LITERAL.matcher(linha).find())
                .toList();

        assertThat(violacoes)
                .as("segredos runtime devem ser apenas referencias de cofre, sem valor literal/default em application.yml")
                .isEmpty();
    }
}
