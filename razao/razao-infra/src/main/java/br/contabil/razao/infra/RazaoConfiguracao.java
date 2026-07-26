package br.contabil.razao.infra;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.razao.application.ConsultarContas;
import br.contabil.razao.application.ConsultarSaldo;
import br.contabil.razao.application.EstornarFatoContabil;
import br.contabil.razao.application.GerarBalancete;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.repository.BalancetePort;
import br.contabil.razao.domain.repository.CatalogoContasPort;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

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
            Clock clock) {
        return new RegistrarFatoContabil(controleAcesso, repositorio, contadorFato, periodoContabil, clock);
    }

    @Bean
    public EstornarFatoContabil estornarFatoContabil(
            ControleAcesso controleAcesso,
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            PeriodoContabilPort periodoContabil,
            Clock clock) {
        return new EstornarFatoContabil(controleAcesso, repositorio, contadorFato, periodoContabil, clock);
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
}
