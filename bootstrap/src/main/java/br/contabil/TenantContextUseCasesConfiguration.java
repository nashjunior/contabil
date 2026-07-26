package br.contabil;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.TenantContext;
import br.contabil.plataforma.domain.TenantId;

/**
 * Fecha o wiring que faltava para a RLS forçada funcionar (RAZ-21, ADR-0003,
 * razao-contabil-schema.md §Trava 4): dentro da MESMA transação/conexão aberta por
 * {@link TransacaoUseCasesConfiguration}, seta a variável de sessão
 * {@code app.ente_id} a partir do {@link TenantId} que o chamador já passa
 * verificado como argumento de {@code executar(..)} — nunca de header/query/body
 * cru (regra de tenant do guardiao-seguranca).
 *
 * <p>Sem isto, toda policy RLS (deny-by-default) nega tudo e
 * {@code proximo_numero_seq()} falha com "sem contador de fato inicializado": o
 * pipeline de escrita do razão não roda contra um Postgres real com a trava 4
 * forçada — só os testes de migration setavam a variável, manualmente, via JDBC
 * cru.
 *
 * <p>RAZ-23: o mesmo {@link TenantId} extraído também ativa {@link
 * TenantContext} para a duração da chamada — não só o razão precisa do ente
 * corrente; qualquer código chamado de dentro do {@code executar(..)} advisado
 * (ex.: {@code ServicoAssinaturaGovBrAvancada}, RAZ-11) pode ler {@link
 * TenantContext#atual()} sem receber o tenant como costura própria.
 */
@Configuration
public class TenantContextUseCasesConfiguration {

    /**
     * Roda DEPOIS que a transação já abriu — ordem maior (menor precedência) que
     * {@link TransacaoUseCasesConfiguration#TRANSACTION_ADVISOR_ORDER}, para
     * ficar mais interno na cadeia de advisors e usar a conexão já vinculada à
     * transação corrente.
     */
    private static final int TENANT_CONTEXT_ADVISOR_ORDER = TransacaoUseCasesConfiguration.TRANSACTION_ADVISOR_ORDER + 1;

    /**
     * {@code set_config} (não {@code SET LOCAL} com concatenação de string) para
     * vincular o valor com bind parameter — o {@code true} final replica o escopo
     * de {@code SET LOCAL}: some no commit/rollback da transação corrente.
     */
    private static final String SQL_SET_TENANT = "select set_config('app.ente_id', ?, true)";

    /** Seta {@code app.ente_id} a partir do {@link TenantId} de todo {@code executar(..)} da application. */
    @Bean
    public Advisor tenantContextUseCasesAdvisor(JdbcTemplate jdbcTemplate) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* br.contabil..application..*.executar(..))");
        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, new TenantContextInterceptor(jdbcTemplate));
        advisor.setOrder(TENANT_CONTEXT_ADVISOR_ORDER);
        return advisor;
    }

    private static final class TenantContextInterceptor implements MethodInterceptor {

        private final JdbcTemplate jdbcTemplate;

        private TenantContextInterceptor(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        @SuppressWarnings("try") // escopoTenant serve só para o close() automático desativar o TenantContext no fim do escopo
        public Object invoke(MethodInvocation invocation) throws Throwable {
            TenantId enteId = extrairTenantId(invocation);
            jdbcTemplate.queryForObject(SQL_SET_TENANT, String.class, enteId.valor().toString());
            try (AutoCloseable escopoTenant = TenantContext.ativarPara(enteId)) {
                return invocation.proceed();
            }
        }

        /**
         * Fail-closed: {@code executar(..)} sem {@link TenantId} entre os argumentos
         * é erro de wiring do use case, não um caso a ignorar em silêncio — deixar
         * passar sem setar {@code app.ente_id} devolveria "zero linhas" (deny-by-default)
         * em vez de estourar alto onde o bug está.
         */
        private static TenantId extrairTenantId(MethodInvocation invocation) {
            for (Object argumento : invocation.getArguments()) {
                if (argumento instanceof TenantId tenantId) {
                    return tenantId;
                }
            }
            throw new IllegalStateException(
                    "executar(..) de %s não recebe TenantId — impossível setar app.ente_id (RLS deny-by-default negaria tudo)"
                            .formatted(invocation.getMethod().getDeclaringClass().getName()));
        }
    }
}
