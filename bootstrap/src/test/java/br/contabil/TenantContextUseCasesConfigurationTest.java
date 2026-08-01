package br.contabil;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.aopalliance.intercept.MethodInterceptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantContext;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

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
        PeriodoContabilId periodoId = PeriodoContabilId.novo();
        LocalDate dataCompetencia = LocalDate.of(2026, 7, 1);
        AtomicReference<Optional<TenantId>> tenantContextDuranteAChamada = new AtomicReference<>();
        when(periodoContabil.periodoAbertoPara(enteId, dataCompetencia)).thenAnswer(invocacao -> {
            tenantContextDuranteAChamada.set(TenantContext.atual());
            return periodoId;
        });
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        assertThat(TenantContext.atual()).isEmpty();

        ServicoIdentidade servicoIdentidadePermissivo = mock(ServicoIdentidade.class);
        when(servicoIdentidadePermissivo.autorizar(any(), any(), any())).thenReturn(true);
        Sessao sessao = new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), true,
                Instant.parse("2030-01-01T00:00:00Z"));

        AuditoriaEscrita auditoria = mock(AuditoriaEscrita.class);
        RegistrarFatoContabil alvo = new RegistrarFatoContabil(
                new ControleAcesso(servicoIdentidadePermissivo), repositorio, contadorFato, periodoContabil, auditoria,
                relogioFixo);
        RegistrarFatoContabil proxy = comAdvisorAplicado(alvo, jdbcTemplate);

        proxy.executar(
                sessao,
                enteId,
                dataCompetencia,
                TipoEvento.EMPENHO,
                "histórico",
                "origem",
                List.of(
                        Lancamento.de(ContaContabilId.novo(), Natureza.DEBITO, Dinheiro.de("10.00")),
                        Lancamento.de(ContaContabilId.novo(), Natureza.CREDITO, Dinheiro.de("10.00"))));

        InOrder ordem = inOrder(jdbcTemplate, periodoContabil, repositorio);
        ordem.verify(jdbcTemplate)
                .queryForObject(eq(SQL_SET_TENANT), eq(String.class), eq(enteId.valor().toString()));
        ordem.verify(periodoContabil).periodoAbertoPara(enteId, dataCompetencia);
        ordem.verify(repositorio).inserir(any());

        assertThat(tenantContextDuranteAChamada.get())
                .as("RAZ-23: TenantContext precisa estar ativo quando o use case toca as ports, não só o GUC do banco")
                .contains(enteId);
        assertThat(TenantContext.atual())
                .as("o escopo fecha quando invocation.proceed() retorna — não pode vazar para fora da chamada advisada")
                .isEmpty();
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
        assertThat(TenantContext.atual())
                .as("falha antes de extrair o TenantId não deve deixar o TenantContext ativo")
                .isEmpty();
    }

    @Test
    void useCaseQueLancaExcecaoNaoDeixaTenantContextVazado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TenantId enteId = TenantId.de(UUID.randomUUID().toString());
        UseCaseQueLanca alvo = semUso -> {
            throw new IllegalStateException("falha simulada dentro do use case, depois do escopo já ativo");
        };
        UseCaseQueLanca proxy = comAdvisorAplicado(alvo, jdbcTemplate, UseCaseQueLanca.class);

        assertThatThrownBy(() -> proxy.executar(enteId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha simulada dentro do use case, depois do escopo já ativo");

        assertThat(TenantContext.atual())
                .as("try-with-resources fecha o escopo mesmo quando invocation.proceed() lança — sem isso, "
                        + "o tenant do use case que falhou vazaria para a próxima chamada na mesma thread (pool)")
                .isEmpty();
    }

    interface UseCaseSemTenant {
        void executar(String semTenant);
    }

    interface UseCaseQueLanca {
        void executar(TenantId enteId);
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
