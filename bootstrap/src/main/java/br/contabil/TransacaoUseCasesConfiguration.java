package br.contabil;

import java.util.Objects;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Transação declarativa na BORDA (ADR-0002 / AGENTS.md): a {@code application} é
 * POJO sem {@code @Transactional}; a infra/composition-root demarca a transação.
 *
 * <p>Todo método {@code executar(..)} de um pacote {@code ..application..} roda
 * dentro de uma transação (propagação REQUIRED, rollback em RuntimeException) via
 * um advisor AOP — sem tocar no código de aplicação.
 */
@Configuration
@EnableTransactionManagement
@EnableAspectJAutoProxy
public class TransacaoUseCasesConfiguration {

    /**
     * Ordem do advisor de transação — precisa ser o mais EXTERNO da cadeia (menor
     * valor = maior precedência) para que a transação já esteja aberta, com a
     * conexão vinculada, quando {@code TenantContextUseCasesConfiguration} rodar
     * {@code SET LOCAL app.ente_id} (RAZ-21): sem transação aberta antes, o
     * {@code set_config(..., true)} setaria a variável fora de qualquer escopo
     * transacional útil.
     */
    static final int TRANSACTION_ADVISOR_ORDER = 0;

    /** Aplica transação a todo {@code executar(..)} dos use cases da application. */
    @Bean
    public Advisor transacaoUseCasesAdvisor(TransactionManager transactionManager) {
        Objects.requireNonNull(transactionManager, "transactionManager");
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* br.contabil..application..*.executar(..))");
        TransactionInterceptor interceptor =
                new TransactionInterceptor(transactionManager, new MatchAlwaysTransactionAttributeSource());
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
        advisor.setOrder(TRANSACTION_ADVISOR_ORDER);
        return advisor;
    }
}
