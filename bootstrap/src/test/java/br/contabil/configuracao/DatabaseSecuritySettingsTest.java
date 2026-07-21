package br.contabil.configuracao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class DatabaseSecuritySettingsTest {

    @Test
    void aceitaRuntimeEMigracaoComUsuariosDistintosETls() {
        var settings = new DatabaseSecuritySettings(
                "jdbc:postgresql://db:5432/razao?sslmode=require",
                "razao_app",
                "valor-runtime-teste",
                "jdbc:postgresql://db:5432/razao?sslmode=verify-full",
                "razao_migration",
                "valor-migracao-teste",
                true);

        assertThatCode(settings::validar).doesNotThrowAnyException();
    }

    @Test
    void rejeitaSenhaAusenteParaEvitarDefaultLiteral() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new DatabaseSecuritySettings(
                        "jdbc:postgresql://db:5432/razao?sslmode=require",
                        "razao_app",
                        "",
                        "jdbc:postgresql://db:5432/razao?sslmode=require",
                        "razao_migration",
                        "valor-migracao-teste",
                        true))
                .withMessage("spring.datasource.password deve vir do cofre/ambiente e nao pode ser vazio");
    }

    @Test
    void rejeitaRuntimeSemSslmode() {
        var settings = new DatabaseSecuritySettings(
                "jdbc:postgresql://db:5432/razao",
                "razao_app",
                "valor-runtime-teste",
                "jdbc:postgresql://db:5432/razao?sslmode=require",
                "razao_migration",
                "valor-migracao-teste",
                true);

        assertThatIllegalStateException()
                .isThrownBy(settings::validar)
                .withMessage("spring.datasource.url deve declarar sslmode=require, verify-ca ou verify-full para PostgreSQL");
    }

    @Test
    void aceitaLoopbackSemSslmodeMesmoComExigenciaAtiva() {
        for (String host : java.util.List.of("localhost", "127.0.0.1", "[::1]")) {
            var settings = new DatabaseSecuritySettings(
                    "jdbc:postgresql://" + host + ":5432/test",
                    "razao_app",
                    "valor-runtime-teste",
                    "jdbc:postgresql://" + host + ":5432/test",
                    "razao_migration",
                    "valor-migracao-teste",
                    true);

            assertThatCode(settings::validar).doesNotThrowAnyException();
        }
    }

    @Test
    void rejeitaMesmoUsuarioParaRuntimeEFlyway() {
        var settings = new DatabaseSecuritySettings(
                "jdbc:postgresql://db:5432/razao?sslmode=require",
                "razao_owner",
                "valor-runtime-teste",
                "jdbc:postgresql://db:5432/razao?sslmode=require",
                "razao_owner",
                "valor-migracao-teste",
                true);

        assertThatIllegalStateException()
                .isThrownBy(settings::validar)
                .withMessageContaining("credenciais de runtime e migracao devem ser usuarios distintos");
    }

    @Test
    void permiteDesligarSslSomenteParaFixtureDeTesteSemTls() {
        var settings = new DatabaseSecuritySettings(
                "jdbc:postgresql://localhost:5432/test",
                "razao_app",
                "valor-runtime-teste",
                "jdbc:postgresql://localhost:5432/test",
                "razao_migration",
                "valor-migracao-teste",
                false);

        assertThatCode(settings::validar).doesNotThrowAnyException();
    }
}
