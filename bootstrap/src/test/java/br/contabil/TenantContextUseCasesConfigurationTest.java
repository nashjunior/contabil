package br.contabil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testa o advisor de RAZ-21 isolado (Spring AOP em memória via {@link ProxyFactory},
 * sem Postgres real) — o contrato de RLS/{@code app.ente_id} contra um banco de
 * verdade, através do wiring de produção completo, é coberto por
 * {@link TenantContextWiringIntegrationTest}.
 *
 * <p>Usa {@link RegistrarFatoContabil} (classe real de produção, não um fixture)
 * como alvo para provar, em ordem, que {@code select set_config('app.ente_id', ?,
 * true)} roda ANTES do use case tocar qualquer port — exatamente o que faltava
 * (a application só recebia {@link TenantId} como argumento, mas nada setava a
 * variável de sessão que a RLS forçada exige).
 */
class TenantContextUseCasesConfigurationTest {

    private static final String SQL_SET_TENANT = "select set_config('app.ente_id', ?, true)";

    @Test
    void setaAppEnteIdAntesDeQualquerPortDoUseCaseSerChamada() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FatoContabilRepository repositorio = mock(FatoContabilRepository.class);
        ContadorFatoPort contadorFato = mock(ContadorFatoPort.class);
        PeriodoContabilPort periodoContabil = mock(PeriodoContabilPort.class);
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);

        TenantId enteId = TenantId.de(UUID.randomUUID().toString());
        UUID periodoId = UUID.randomUUID();
        LocalDate dataCompetencia = LocalDate.of(2026, 7, 1);
        when(periodoContabil.periodoAbertoPara(enteId, dataCompetencia)).thenReturn(periodoId);
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        RegistrarFatoContabil alvo = new RegistrarFatoContabil(repositorio, contadorFato, periodoContabil, relogioFixo);
        RegistrarFatoContabil proxy = comAdvisorAplicado(alvo, jdbcTemplate);

        proxy.executar(
                enteId,
                dataCompetencia,
                TipoEvento.EMPENHO,
                "histórico",
                "origem",
                List.of(
                        Lancamento.de(UUID.randomUUID(), Natureza.DEBITO, Dinheiro.de("10.00")),
                        Lancamento.de(UUID.randomUUID(), Natureza.CREDITO, Dinheiro.de("10.00"))));

        InOrder ordem = inOrder(jdbcTemplate, periodoContabil, repositorio);
        ordem.verify(jdbcTemplate)
                .queryForObject(eq(SQL_SET_TENANT), eq(String.class), eq(enteId.valor().toString()));
        ordem.verify(periodoContabil).periodoAbertoPara(enteId, dataCompetencia);
        ordem.verify(repositorio).inserir(any());
    }

    @Test
    void executarSemTenantIdNosArgumentosFalhaAltoSemTocarNoUseCaseNemNoBanco() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicBoolean chamouOAlvo = new AtomicBoolean(false);
        UseCaseSemTenant alvo = semTenant -> chamouOAlvo.set(true);
        UseCaseSemTenant proxy = comAdvisorAplicado(alvo, jdbcTemplate, UseCaseSemTenant.class);

        assertThatIllegalStateException()
                .isThrownBy(() -> proxy.executar("sem tenant nos argumentos"))
                .withMessageContaining("app.ente_id");

        verifyNoInteractions(jdbcTemplate);
        assertThat(chamouOAlvo.get()).isFalse();
    }

    interface UseCaseSemTenant {
        void executar(String semTenant);
    }

    /** Aplica só o {@link MethodInterceptor} do advisor — dispensa casar o pointcut AspectJ por pacote. */
    private static <T> T comAdvisorAplicado(T alvo, JdbcTemplate jdbcTemplate) {
        @SuppressWarnings("unchecked")
        Class<T> tipoAlvo = (Class<T>) alvo.getClass();
        return comAdvisorAplicado(alvo, jdbcTemplate, tipoAlvo);
    }

    private static <T> T comAdvisorAplicado(T alvo, JdbcTemplate jdbcTemplate, Class<T> tipoProxy) {
        Advisor advisor = new TenantContextUseCasesConfiguration().tenantContextUseCasesAdvisor(jdbcTemplate);
        MethodInterceptor interceptor = (MethodInterceptor) advisor.getAdvice();

        ProxyFactory factory = new ProxyFactory(alvo);
        factory.addAdvice(interceptor);
        Object proxy = factory.getProxy();
        assertThat(tipoProxy.isInstance(proxy)).isTrue();
        return tipoProxy.cast(proxy);
    }
}
