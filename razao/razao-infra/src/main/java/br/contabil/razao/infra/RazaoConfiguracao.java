package br.contabil.razao.infra;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.razao.application.CancelarRestoAPagar;
import br.contabil.razao.application.ConsultarContas;
import br.contabil.razao.application.ConsultarSaldo;
import br.contabil.razao.application.EncerrarExercicio;
import br.contabil.razao.application.EncerrarPeriodo;
import br.contabil.razao.application.EstornarFatoContabil;
import br.contabil.razao.application.GerarBalancete;
import br.contabil.razao.application.GerarDemonstracoesDcasp;
import br.contabil.razao.application.InscreverRestosAPagar;
import br.contabil.razao.application.ParametroInscricaoRP;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.repository.BalancetePort;
import br.contabil.razao.domain.repository.CatalogoContasPort;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.DemonstracoesDcaspPort;
import br.contabil.razao.domain.repository.DisponibilidadePorFontePort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

/**
 * Wiring dos use cases do razão (ADR-0002 / AGENTS.md).
 *
 * <p>Os use cases da {@code application} são POJOs sem anotação de framework; é a
 * infra (composition root do contexto) que os declara como beans, injetando os
 * adapters — implementações das interfaces de {@code domain.repository}
 * ({@link FatoContabilRepository}, {@link ContadorFatoPort},
 * {@link PeriodoContabilPort}), o {@link ControleAcesso} (RAZ-33, wiring em
 * {@code plataforma-infra.iam.IdentidadeConfiguracao}) — e o {@link Clock}.
 *
 * <p>A <b>transação</b> é aplicada na borda por {@code TransacaoUseCasesConfiguration}
 * (bootstrap) sobre todo {@code executar(..)} de {@code ..application..}, não por
 * anotação no use case.
 */
@Configuration
public class RazaoConfiguracao {

    @Bean
    public RegistrarFatoContabil registrarFatoContabil(
            ControleAcesso controleAcesso,
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new RegistrarFatoContabil(
                controleAcesso, repositorio, contadorFato, periodoContabil, auditoria, clock);
    }

    @Bean
    public EstornarFatoContabil estornarFatoContabil(
            ControleAcesso controleAcesso,
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new EstornarFatoContabil(
                controleAcesso, repositorio, contadorFato, periodoContabil, auditoria, clock);
    }

    /**
     * RAZ-59: fecha o gap de {@link ConsultaSaldoPort} sem use case (fora do advisor de tenant).
     * RAZ-117: injeta {@link CatalogoContasPort} para validar a existência da conta (gap 2 da ADR-0030).
     */
    @Bean
    public ConsultarSaldo consultarSaldo(
            ControleAcesso controleAcesso, ConsultaSaldoPort consultaSaldo, CatalogoContasPort catalogo) {
        return new ConsultarSaldo(controleAcesso, consultaSaldo, catalogo);
    }

    /** RAZ-117: catálogo/busca de contas do PCASP (ADR-0030 §6) — o read port que faltava. */
    @Bean
    public ConsultarContas consultarContas(ControleAcesso controleAcesso, CatalogoContasPort catalogo) {
        return new ConsultarContas(controleAcesso, catalogo);
    }

    /** RAZ-97: balancete de encerramento/comparativo entre períodos. */
    @Bean
    public GerarBalancete gerarBalancete(ControleAcesso controleAcesso, BalancetePort balancete) {
        return new GerarBalancete(controleAcesso, balancete);
    }

    /** RAZ-241: demonstrações DCASP via read models do razão, sem snapshot materializado. */
    @Bean
    public GerarDemonstracoesDcasp gerarDemonstracoesDcasp(
            ControleAcesso controleAcesso, DemonstracoesDcaspPort demonstracoes) {
        return new GerarDemonstracoesDcasp(controleAcesso, demonstracoes);
    }

    /** RAZ-206: encerramento de período comum (mês 1-12) — ADR-0045/ADR-0046. */
    @Bean
    public EncerrarPeriodo encerrarPeriodo(
            ControleAcesso controleAcesso,
            PeriodoContabilRepository periodoRepositorio,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new EncerrarPeriodo(controleAcesso, periodoRepositorio, auditoria, clock);
    }

    /** RAZ-207: colaborador interno para inscrição append-only de Restos a Pagar no encerramento. */
    @Bean
    public InscreverRestosAPagar inscreverRestosAPagar(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            DisponibilidadePorFontePort disponibilidadePorFonte,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new InscreverRestosAPagar(repositorio, contadorFato, disponibilidadePorFonte, auditoria, clock);
    }

    /** RAZ-207: cancelamento append-only de inscrição de RP. */
    @Bean
    public CancelarRestoAPagar cancelarRestoAPagar(
            ControleAcesso controleAcesso,
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new CancelarRestoAPagar(
                controleAcesso, repositorio, contadorFato, periodoContabil, auditoria, clock);
    }

    /** RAZ-226: encerramento de exercício (mês 13) com inscrição de RP entregue por RAZ-207. */
    @Bean
    public EncerrarExercicio encerrarExercicio(
            ControleAcesso controleAcesso,
            PeriodoContabilRepository periodoRepositorio,
            InscreverRestosAPagar inscreverRP,
            AuditoriaEscrita auditoria,
            Clock clock) {
        List<ParametroInscricaoRP> parametrosRP = List.of();
        return new EncerrarExercicio(controleAcesso, periodoRepositorio, inscreverRP, parametrosRP, auditoria, clock);
    }
}
