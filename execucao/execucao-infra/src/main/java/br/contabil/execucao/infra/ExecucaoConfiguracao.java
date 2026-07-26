package br.contabil.execucao.infra;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.contabil.execucao.application.AprovarPagamento;
import br.contabil.execucao.application.AssinarEmpenho;
import br.contabil.execucao.application.ConsultarExecucaoOrcamentaria;
import br.contabil.execucao.application.IngerirDotacoes;
import br.contabil.execucao.application.PublicacaoTransparenciaExecucaoPort;
import br.contabil.execucao.application.PublicadorTransparenciaExecucao;
import br.contabil.execucao.application.RegistrarEmpenho;
import br.contabil.execucao.application.RegistrarLiquidacao;
import br.contabil.execucao.application.RegistrarPagamento;
import br.contabil.execucao.application.SinalizacaoSlaTransparenciaPort;
import br.contabil.execucao.application.SolicitacaoDocumentoAssinaturaPort;
import br.contabil.execucao.domain.repository.ContadorEmpenhoPort;
import br.contabil.execucao.domain.repository.DotacaoRepository;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoOrcamentariaPeriodoPort;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
import br.contabil.execucao.domain.repository.PagamentoRepository;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
import br.contabil.plataforma.domain.assinatura.NivelAssinaturaExigidoPort;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.entrega.ServicoEntrega;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento;

/**
 * Wiring dos use cases da execução (ADR-0002 / AGENTS.md).
 *
 * <p>{@link RegistrarEmpenho} é POJO sem anotação de framework; é a infra
 * (composition root do contexto) que o declara como bean, injetando os
 * adapters. {@link ExecucaoContabilPort} é implementado no {@code bootstrap}
 * (único módulo que conhece tanto {@code execucao} quanto {@code razao}) —
 * este bean chega aqui por tipo, sem {@code execucao-infra} depender de
 * {@code razao} (execucao-orcamentaria-despesa.md §Fronteiras).
 */
@Configuration
public class ExecucaoConfiguracao {

    @Bean
    public RegistrarEmpenho registrarEmpenho(
            ControleAcesso controleAcesso,
            SaldosExecucaoPort saldos,
            ExecucaoContabilPort escrituracao,
            ContadorEmpenhoPort contadorEmpenho,
            EmpenhoRepository repositorio,
            PublicacaoTransparenciaExecucaoPort publicacaoTransparencia,
            SolicitacaoDocumentoAssinaturaPort solicitacaoDocumentoAssinatura,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new RegistrarEmpenho(
                controleAcesso,
                saldos,
                escrituracao,
                contadorEmpenho,
                repositorio,
                publicacaoTransparencia,
                solicitacaoDocumentoAssinatura,
                auditoria,
                clock);
    }

    /**
     * Só existe quando {@link ServicoAssinatura} está configurado (ADR-0027 (c);
     * mesmo pré-requisito de {@code ServicoAssinaturaGovBrConfiguration}, que
     * declara esse bean condicionalmente) — fail-fast na composição, nunca
     * fail-open no fluxo de assinatura.
     */
    @Bean
    @ConditionalOnBean(ServicoAssinatura.class)
    public AssinarEmpenho assinarEmpenho(
            ControleAcesso controleAcesso,
            EmpenhoRepository repositorio,
            NivelAssinaturaExigidoPort nivelAssinaturaExigido,
            ServicoAssinatura servicoAssinatura,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new AssinarEmpenho(controleAcesso, repositorio, nivelAssinaturaExigido, servicoAssinatura, auditoria, clock);
    }

    /** RAZ-105: fecha o wiring que RAZ-67 (use case) deixou pendente — mesmo padrão de {@link #registrarEmpenho}. */
    @Bean
    public RegistrarLiquidacao registrarLiquidacao(
            ControleAcesso controleAcesso,
            SaldosExecucaoPort saldos,
            ExecucaoContabilPort escrituracao,
            LiquidacaoRepository repositorio,
            PublicacaoTransparenciaExecucaoPort publicacaoTransparencia,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new RegistrarLiquidacao(
                controleAcesso, saldos, escrituracao, repositorio, publicacaoTransparencia, auditoria, clock);
    }

    /** RAZ-105: fecha o wiring que RAZ-67 (use case) deixou pendente — mesmo padrão de {@link #registrarEmpenho}. */
    @Bean
    public RegistrarPagamento registrarPagamento(
            ControleAcesso controleAcesso,
            LiquidacaoRepository liquidacaoRepositorio,
            SaldosExecucaoPort saldos,
            ExecucaoContabilPort escrituracao,
            PagamentoRepository repositorio,
            PublicacaoTransparenciaExecucaoPort publicacaoTransparencia,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new RegistrarPagamento(
                controleAcesso, liquidacaoRepositorio, saldos, escrituracao, repositorio, publicacaoTransparencia,
                auditoria, clock);
    }

    /** RAZ-105: fecha o wiring que RAZ-92 (use case, gate ADR-0023) deixou pendente. */
    @Bean
    public AprovarPagamento aprovarPagamento(
            ControleAcesso controleAcesso,
            LiquidacaoRepository liquidacaoRepositorio,
            EmpenhoRepository empenhoRepositorio,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new AprovarPagamento(controleAcesso, liquidacaoRepositorio, empenhoRepositorio, auditoria, clock);
    }

    @Bean
    public IngerirDotacoes ingerirDotacoes(
            ControleAcesso controleAcesso, DotacaoRepository repositorio, AuditoriaEscrita auditoria, Clock clock) {
        return new IngerirDotacoes(controleAcesso, repositorio, auditoria, clock);
    }

    /** RAZ-97: execução orçamentária agregada (empenhado/liquidado/pago) por período/ente. */
    @Bean
    public ConsultarExecucaoOrcamentaria consultarExecucaoOrcamentaria(
            ControleAcesso controleAcesso, ExecucaoOrcamentariaPeriodoPort execucaoOrcamentaria) {
        return new ConsultarExecucaoOrcamentaria(controleAcesso, execucaoOrcamentaria);
    }

    @Bean
    public PublicacaoTransparenciaExecucaoPort publicacaoTransparenciaExecucao(
            ServicoEntrega entrega,
            ServicoMascaramento mascaramento,
            SinalizacaoSlaTransparenciaPort sinalizacaoSla,
            Clock clock) {
        return new PublicadorTransparenciaExecucao(entrega, mascaramento, sinalizacaoSla, clock);
    }
}
