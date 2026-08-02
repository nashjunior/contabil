package br.contabil.razao.infra;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.razao.application.ApurarResultadoPatrimonial;
import br.contabil.razao.application.CancelarRestoAPagar;
import br.contabil.razao.application.ConsultarContas;
import br.contabil.razao.application.ConsultarSaldo;
import br.contabil.razao.application.EncerrarContasOrcamentarias;
import br.contabil.razao.application.EncerrarDdrPorFonte;
import br.contabil.razao.application.EncerrarExercicio;
import br.contabil.razao.application.EncerrarPeriodo;
import br.contabil.razao.application.EstornarFatoContabil;
import br.contabil.razao.application.GerarBalancete;
import br.contabil.razao.application.GerarDemonstracoesDcasp;
import br.contabil.razao.application.InscreverRestosAPagar;
import br.contabil.razao.application.ParametroEncerramentoOrcamentario;
import br.contabil.razao.application.ParametroInscricaoRP;
import br.contabil.razao.application.ParametroTransposicaoAbertura;
import br.contabil.razao.application.ParametrosEncerramentoDdr;
import br.contabil.razao.application.ParametrosTransposicaoDdrAbertura;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.application.TransporDdrPorFonteAbertura;
import br.contabil.razao.application.TransporSaldosAbertura;
import br.contabil.razao.domain.ContaContabilId;
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

    /**
     * RAZ-257: colaborador interno para apuração append-only do resultado patrimonial
     * (VPA/VPD classes 3/4) no encerramento do exercício.
     */
    @Bean
    public ApurarResultadoPatrimonial apurarResultadoPatrimonial(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            BalancetePort balancete,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new ApurarResultadoPatrimonial(repositorio, contadorFato, balancete, auditoria, clock);
    }

    /**
     * RAZ-258: colaborador interno para encerramento append-only das contas de controle
     * orçamentário PCASP classes 5/6 no encerramento do exercício.
     */
    @Bean
    public EncerrarContasOrcamentarias encerrarContasOrcamentarias(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            ConsultaSaldoPort consultaSaldo,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new EncerrarContasOrcamentarias(repositorio, contadorFato, consultaSaldo, auditoria, clock);
    }

    /**
     * RAZ-244: colaborador interno para encerramento append-only da DDR (classes 7/8)
     * utilizada por fonte no encerramento do exercício (IPC 03/STN §91).
     */
    @Bean
    public EncerrarDdrPorFonte encerrarDdrPorFonte(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            DisponibilidadePorFontePort disponibilidadePorFonte,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new EncerrarDdrPorFonte(repositorio, contadorFato, disponibilidadePorFonte, auditoria, clock);
    }

    /**
     * RAZ-266: configuração oficial de encerramento da DDR por código PCASP, resolvida
     * por ente porque {@code conta_pcasp.id} é tenant-scoped.
     */
    @Bean
    public ParametrosEncerramentoDdr parametrosEncerramentoDdr(JdbcTemplate jdbcTemplate) {
        return new ParametrosEncerramentoDdrOficiais(jdbcTemplate);
    }

    /**
     * RAZ-259: colaborador interno para a abertura do exercício seguinte — transposição
     * append-only de saldos patrimoniais permanentes em 1º/jan (docs/15 §Preciso item 5,
     * IPC 03/STN item 30).
     */
    @Bean
    public TransporSaldosAbertura transporSaldosAbertura(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            ConsultaSaldoPort consultaSaldo,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new TransporSaldosAbertura(repositorio, contadorFato, consultaSaldo, periodoContabil, auditoria, clock);
    }

    /**
     * RAZ-244: colaborador interno para a abertura da DDR — transposição append-only do
     * superávit financeiro por fonte em 1º/jan (docs/15 §Preciso item 5, IPC 03/STN §96).
     */
    @Bean
    public TransporDdrPorFonteAbertura transporDdrPorFonteAbertura(
            FatoContabilRepository repositorio,
            ContadorFatoPort contadorFato,
            DisponibilidadePorFontePort disponibilidadePorFonte,
            PeriodoContabilPort periodoContabil,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new TransporDdrPorFonteAbertura(
                repositorio, contadorFato, disponibilidadePorFonte, periodoContabil, auditoria, clock);
    }

    /**
     * RAZ-266: configuração oficial de abertura da DDR por código PCASP, resolvida por
     * ente porque {@code conta_pcasp.id} é tenant-scoped.
     */
    @Bean
    public ParametrosTransposicaoDdrAbertura parametrosTransposicaoDdrAbertura(JdbcTemplate jdbcTemplate) {
        return new ParametrosTransposicaoDdrAberturaOficiais(jdbcTemplate);
    }

    /**
     * RAZ-226/RAZ-257/RAZ-258/RAZ-244/RAZ-259: encerramento de exercício (mês 13) com apuração
     * do resultado patrimonial classes 3/4 (RAZ-257), inscrição de RP (RAZ-207), encerramento
     * das contas de controle orçamentário classes 5/6 (RAZ-258), encerramento da DDR
     * utilizada por fonte classes 7/8 (RAZ-244) e abertura do exercício seguinte, incluindo o
     * superávit financeiro da DDR por fonte (RAZ-259/RAZ-244).
     *
     * <p>{@code contaResultadoApurado} segue {@link Optional#empty()} e
     * {@code parametrosRP}/{@code parametrosEncerramentoOrcamentario}/{@code parametrosAbertura}
     * seguem vazios até os códigos PCASP concretos do ente serem revalidados e configurados
     * (mesma postura defensiva do RAZ-207 — nenhuma conta é encerrada/aberta sem parâmetro
     * explícito). Os parâmetros da DDR ({@code parametrosDdr}/{@code parametrosAberturaDdr})
     * já são oficiais e resolvidos por ente via {@link ParametrosEncerramentoDdr}/
     * {@link ParametrosTransposicaoDdrAbertura} (RAZ-266).
     */
    @Bean
    public EncerrarExercicio encerrarExercicio(
            ControleAcesso controleAcesso,
            PeriodoContabilRepository periodoRepositorio,
            ApurarResultadoPatrimonial apurarResultadoPatrimonial,
            InscreverRestosAPagar inscreverRP,
            EncerrarContasOrcamentarias encerrarContasOrcamentarias,
            EncerrarDdrPorFonte encerrarDdr,
            ParametrosEncerramentoDdr parametrosDdr,
            TransporSaldosAbertura transporSaldosAbertura,
            TransporDdrPorFonteAbertura transporDdrAbertura,
            ParametrosTransposicaoDdrAbertura parametrosAberturaDdr,
            AuditoriaEscrita auditoria,
            Clock clock) {
        Optional<ContaContabilId> contaResultadoApurado = Optional.empty();
        List<ParametroInscricaoRP> parametrosRP = List.of();
        List<ParametroEncerramentoOrcamentario> parametrosEncerramentoOrcamentario = List.of();
        List<ParametroTransposicaoAbertura> parametrosAbertura = List.of();
        return new EncerrarExercicio(
                controleAcesso,
                periodoRepositorio,
                apurarResultadoPatrimonial,
                contaResultadoApurado,
                inscreverRP,
                parametrosRP,
                encerrarContasOrcamentarias,
                parametrosEncerramentoOrcamentario,
                encerrarDdr,
                parametrosDdr,
                transporSaldosAbertura,
                parametrosAbertura,
                transporDdrAbertura,
                parametrosAberturaDdr,
                auditoria,
                clock);
    }
}
