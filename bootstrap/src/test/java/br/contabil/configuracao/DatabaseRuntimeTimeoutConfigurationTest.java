package br.contabil.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseRuntimeTimeoutConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:postgresql://localhost:5432/razao",
                    "spring.datasource.username=app_login",
                    "spring.datasource.password=app_login",
                    "spring.datasource.hikari.connection-init-sql=SET statement_timeout = '30s'; SET lock_timeout = '5s'",
                    "spring.jdbc.template.query-timeout=30");

    @Test
    void datasourceRuntimeTemStatementELockTimeoutEJdbcTemplateQueryTimeout() {
        contextRunner.run(context -> {
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

            assertThat(dataSource.getConnectionInitSql())
                    .contains("statement_timeout")
                    .contains("lock_timeout");
            assertThat(jdbcTemplate.getQueryTimeout()).isEqualTo(30);
        });
    }
}
