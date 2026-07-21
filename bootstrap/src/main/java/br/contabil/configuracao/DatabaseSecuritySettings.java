package br.contabil.configuracao;

import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;

record DatabaseSecuritySettings(
        String runtimeUrl,
        String runtimeUser,
        String runtimePassword,
        String migrationUrl,
        String migrationUser,
        String migrationPassword,
        boolean exigirSsl) {

    private static final Pattern SSL_MODE_SEGURO =
            Pattern.compile("(?i)(?:[?&])sslmode=(require|verify-ca|verify-full)(?:&|$)");

    private static final String PROP_DATASOURCE_URL = "spring.datasource.url";
    private static final String PROP_DATASOURCE_USERNAME = "spring.datasource.username";
    private static final String PROP_DATASOURCE_PASSWORD = "spring.datasource.password";
    private static final String PROP_FLYWAY_URL = "spring.flyway.url";
    private static final String PROP_FLYWAY_USER = "spring.flyway.user";
    private static final String PROP_FLYWAY_PASSWORD = "spring.flyway.password";

    DatabaseSecuritySettings {
        exigirPresente(runtimeUrl, PROP_DATASOURCE_URL);
        exigirPresente(runtimeUser, PROP_DATASOURCE_USERNAME);
        exigirPresente(runtimePassword, PROP_DATASOURCE_PASSWORD);
        exigirPresente(migrationUrl, PROP_FLYWAY_URL);
        exigirPresente(migrationUser, PROP_FLYWAY_USER);
        exigirPresente(migrationPassword, PROP_FLYWAY_PASSWORD);
    }

    static DatabaseSecuritySettings from(Environment environment) {
        Objects.requireNonNull(environment, "environment nao pode ser nulo");
        return new DatabaseSecuritySettings(
                environment.getProperty(PROP_DATASOURCE_URL),
                environment.getProperty(PROP_DATASOURCE_USERNAME),
                environment.getProperty(PROP_DATASOURCE_PASSWORD),
                environment.getProperty(PROP_FLYWAY_URL),
                environment.getProperty(PROP_FLYWAY_USER),
                environment.getProperty(PROP_FLYWAY_PASSWORD),
                environment.getProperty("siafic.security.database.require-ssl", Boolean.class, true));
    }

    void validar() {
        if (exigirSsl) {
            exigirSslModeSeguro(runtimeUrl, PROP_DATASOURCE_URL);
            exigirSslModeSeguro(migrationUrl, PROP_FLYWAY_URL);
        }
        if (runtimeUser.equals(migrationUser)) {
            throw new IllegalStateException(
                    "credenciais de runtime e migracao devem ser usuarios distintos; "
                            + "runtime deve ser app_role sem DDL e Flyway deve usar conta dona de schema");
        }
    }

    private static void exigirPresente(String valor, String propriedade) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(propriedade + " deve vir do cofre/ambiente e nao pode ser vazio");
        }
    }

    private static void exigirSslModeSeguro(String jdbcUrl, String propriedade) {
        if (ehLoopback(jdbcUrl)) {
            return;
        }
        if (!SSL_MODE_SEGURO.matcher(jdbcUrl).find()) {
            throw new IllegalStateException(
                    propriedade + " deve declarar sslmode=require, verify-ca ou verify-full para PostgreSQL");
        }
    }

    private static boolean ehLoopback(String jdbcUrl) {
        String prefixo = "jdbc:postgresql://";
        if (!jdbcUrl.startsWith(prefixo)) {
            return false;
        }
        String restante = jdbcUrl.substring(prefixo.length());
        int fimAuthority = restante.indexOf('/');
        if (fimAuthority < 0) {
            return false;
        }
        String authority = restante.substring(0, fimAuthority);
        int fimUserInfo = authority.lastIndexOf('@');
        if (fimUserInfo >= 0) {
            authority = authority.substring(fimUserInfo + 1);
        }
        String host;
        if (authority.startsWith("[")) {
            int fimIpv6 = authority.indexOf(']');
            if (fimIpv6 < 0) {
                return false;
            }
            host = authority.substring(1, fimIpv6);
        } else {
            int inicioPorta = authority.indexOf(':');
            host = inicioPorta < 0 ? authority : authority.substring(0, inicioPorta);
        }
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host);
    }
}
