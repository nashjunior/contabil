package br.contabil.plataforma.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Tenant (ente) corrente da requisição/transação em curso — equivalente Java do
 * {@code app.ente_id} de sessão do razão (ADR-0003, RAZ-23). {@link ThreadLocal}:
 * o escopo é a thread que processa a requisição/transação; não atravessa
 * fronteira de thread pool (execução assíncrona, {@code @Async}, pool reativo)
 * sem propagação explícita — quem despachar trabalho para outra thread precisa
 * capturar {@link #atual()} e reabrir o escopo lá com {@link #ativarPara}.
 *
 * <p>Consumidor hoje: o advisor que seta {@code app.ente_id} na conexão do razão
 * ({@code bootstrap.TenantContextUseCasesConfiguration}, RAZ-21), que ativa este
 * escopo para todo {@code executar(..)} advisado — qualquer código chamado
 * transitivamente dali pode ler {@link #atual()} sem receber o tenant como
 * parâmetro extra. {@code ServicoAssinaturaGovBrAvancada} (RAZ-11) resolve o
 * tenant hoje por outro mecanismo ({@code resolvedorDeEnte} injetado); migrar
 * esse adapter para {@link #atual()} não é parte deste diff. O interceptor/filter
 * HTTP que populará isto a partir da identidade autenticada, para código fora de
 * um {@code executar(..)} advisado, depende de RAZ-5 (IAM) e ainda não existe.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> ATUAL = new ThreadLocal<>();

    private TenantContext() {}

    /** Tenant corrente desta thread, se algum escopo o ativou. */
    public static Optional<TenantId> atual() {
        return Optional.ofNullable(ATUAL.get());
    }

    /**
     * Tenant corrente desta thread — fail-closed: estoura em vez de devolver
     * algo silenciosamente errado quando ninguém ativou um escopo (ex.: código
     * chamado fora de um {@code executar(..)} advisado, ou de uma borda HTTP
     * ainda inexistente — RAZ-5).
     */
    public static TenantId exigirAtual() {
        return atual()
                .orElseThrow(() -> new IllegalStateException(
                        "TenantContext sem tenant corrente nesta thread — nenhum interceptor/advisor "
                                + "chamou ativarPara(..) (RAZ-23); chamar fora de um executar(..) advisado "
                                + "é o bug a corrigir, não um caso para tratar em silêncio"));
    }

    /**
     * Ativa {@code tenantId} como corrente nesta thread até o {@link
     * AutoCloseable} devolvido ser fechado — no fechamento, restaura o valor
     * anterior (aninhamento seguro: fechar um escopo interno não apaga um
     * escopo externo ainda aberto).
     */
    public static AutoCloseable ativarPara(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        TenantId anterior = ATUAL.get();
        ATUAL.set(tenantId);
        return () -> {
            if (anterior == null) {
                ATUAL.remove();
            } else {
                ATUAL.set(anterior);
            }
        };
    }
}
