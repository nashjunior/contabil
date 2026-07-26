package br.contabil.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Guardrail estrutural de RAZ-72 (reversibilidade de migração, arquitetura-tecnica §8): toda
 * migração versionada {@code V{n}__{desc}.sql} tem um rollback correspondente
 * {@code R{n}__{desc}_down.sql} em {@code db/rollback}, e nenhum rollback é um stub vazio.
 *
 * <p>Não precisa de Docker — roda sempre em {@code ./gradlew check} e falha o build no instante
 * em que alguém adiciona uma migration sem o seu par de rollback (a regressão que este guardrail
 * existe para pegar). A prova de que o rollback REALMENTE reverte (up→down→up, num Postgres real)
 * fica em {@link ReversibilidadeMigracaoFlywayTest}.
 */
class MigracaoRollbackParidadeTest {

    // V{n}__{desc}.sql  ↔  R{n}__{desc}_down.sql — a chave de comparação é 'n__desc', sem o
    // prefixo V/R nem o sufixo _down.
    private static final Pattern MIGRACAO = Pattern.compile("^V(\\d+)__(.+)\\.sql$");
    private static final Pattern ROLLBACK = Pattern.compile("^R(\\d+)__(.+)_down\\.sql$");

    @Test
    void todaMigracaoVersionadaTemRollbackCorrespondente() throws IOException {
        Set<String> migracoes = chavesDe("classpath*:db/migration/V*.sql", MIGRACAO);
        Set<String> rollbacks = chavesDe("classpath*:db/rollback/R*.sql", ROLLBACK);

        assertThat(migracoes)
                .as("scaffolding: deve haver ao menos uma migração versionada para comparar")
                .isNotEmpty();
        assertThat(rollbacks)
                .as("cada V{n}__{desc}.sql exige um R{n}__{desc}_down.sql e vice-versa "
                        + "(reversibilidade de migração, arquitetura-tecnica §8)")
                .containsExactlyInAnyOrderElementsOf(migracoes);
    }

    @Test
    void nenhumScriptDeRollbackEstaVazio() throws IOException {
        for (Resource script : new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/rollback/R*.sql")) {
            String conteudo = new String(script.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(conteudo.toLowerCase())
                    .as("%s deve conter ao menos um DROP (reverter a migração, não ser um stub)",
                            script.getFilename())
                    .contains("drop");
        }
    }

    private static Set<String> chavesDe(String glob, Pattern padrao) throws IOException {
        Set<String> chaves = new TreeSet<>();
        for (Resource script : new PathMatchingResourcePatternResolver().getResources(glob)) {
            Matcher m = padrao.matcher(String.valueOf(script.getFilename()));
            if (m.matches()) {
                chaves.add(m.group(1) + "__" + m.group(2)); // versão + descrição (sem V/R nem _down)
            }
        }
        return chaves;
    }
}
