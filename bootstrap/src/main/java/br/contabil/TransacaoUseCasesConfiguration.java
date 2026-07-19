package br.contabil;

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

    /** Aplica transação a todo {@code executar(..)} dos use cases da application. */
    @Bean
    public Advisor transacaoUseCasesAdvisor(TransactionManager transactionManager) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* br.contabil..application..*.executar(..))");
        TransactionInterceptor interceptor =
                new TransactionInterceptor(transactionManager, new MatchAlwaysTransactionAttributeSource());
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }
}
