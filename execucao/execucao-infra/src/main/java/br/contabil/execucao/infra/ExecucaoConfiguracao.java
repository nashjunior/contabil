package br.contabil.execucao.infra;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.contabil.execucao.application.AprovarPagamento;
import br.contabil.execucao.application.AssinarEmpenho;
import br.contabil.execucao.application.ConsultarDocumentoEmpenho;
import br.contabil.execucao.application.ConsultarDotacoes;
import br.contabil.execucao.application.ConsultarEmpenhosRegistrados;
import br.contabil.execucao.application.ConsultarExecucaoOrcamentaria;
import br.contabil.execucao.application.ConsultarFilaAprovacao;
import br.contabil.execucao.application.ConsultarLiquidacoesRegistradas;
import br.contabil.execucao.application.ConsultarPagamentosRegistrados;
import br.contabil.execucao.application.ConsultarTrilhaLiquidacao;
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
import br.contabil.execucao.domain.repository.DotacoesQuery;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.execucao.domain.repository.EmpenhosRegistradosQuery;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.ExecucaoOrcamentariaPeriodoPort;
import br.contabil.execucao.domain.repository.FilaAprovacaoQuery;
import br.contabil.execucao.domain.repository.IdempotenciaPagamentoRepository;
import br.contabil.execucao.domain.repository.LiquidacaoRepository;
import br.contabil.execucao.domain.repository.LiquidacoesRegistradasQuery;
import br.contabil.execucao.domain.repository.PagamentoRepository;
import br.contabil.execucao.domain.repository.PagamentosRegistradosQuery;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
import br.contabil.plataforma.domain.assinatura.NivelAssinaturaExigidoPort;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.AuditoriaLeitura;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
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

    /**
     * RAZ-157/ADR-0039 decisão 2: bridge de leitura do PDF atual do empenho — só
     * existe quando o object store está configurado (mesmo pré-requisito de {@link
     * #assinarEmpenho}, sem o qual não há onde ler o documento).
     */
    @Bean
    @ConditionalOnBean(ArmazenamentoDocumentos.class)
    public ConsultarDocumentoEmpenho consultarDocumentoEmpenho(
            ControleAcesso controleAcesso, EmpenhoRepository repositorio, ArmazenamentoDocumentos armazenamento) {
        return new ConsultarDocumentoEmpenho(controleAcesso, repositorio, armazenamento);
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
            IdempotenciaPagamentoRepository idempotencia,
            PublicacaoTransparenciaExecucaoPort publicacaoTransparencia,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new RegistrarPagamento(
                controleAcesso, liquidacaoRepositorio, saldos, escrituracao, repositorio, idempotencia,
                publicacaoTransparencia, auditoria, clock);
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

    /** RAZ-115/ADR-0029 §1: fila de aprovação (read model), segregação Regra 9 imposta na query. */
    @Bean
    public ConsultarFilaAprovacao consultarFilaAprovacao(
            ControleAcesso controleAcesso, FilaAprovacaoQuery filaAprovacao) {
        return new ConsultarFilaAprovacao(controleAcesso, filaAprovacao);
    }

    /** RAZ-115/ADR-0029 §3: trilha de auditoria da liquidação, servida por {@link AuditoriaLeitura}. */
    @Bean
    public ConsultarTrilhaLiquidacao consultarTrilhaLiquidacao(
            ControleAcesso controleAcesso, AuditoriaLeitura auditoriaLeitura) {
        return new ConsultarTrilhaLiquidacao(controleAcesso, auditoriaLeitura);
    }

    /** RAZ-121: registro completo de empenhos por ente/período (read model, gap deixado pelo RAZ-120). */
    @Bean
    public ConsultarEmpenhosRegistrados consultarEmpenhosRegistrados(
            ControleAcesso controleAcesso, EmpenhosRegistradosQuery empenhosRegistrados) {
        return new ConsultarEmpenhosRegistrados(controleAcesso, empenhosRegistrados);
    }

    /** RAZ-148/ADR-0038: dotações por ente/exercício com saldo inline para a tela de gestão. */
    @Bean
    public ConsultarDotacoes consultarDotacoes(ControleAcesso controleAcesso, DotacoesQuery dotacoes) {
        return new ConsultarDotacoes(controleAcesso, dotacoes);
    }

    /** RAZ-121: registro completo de liquidações por ente/período — distinto do gate (fila) de {@link ConsultarFilaAprovacao}. */
    @Bean
    public ConsultarLiquidacoesRegistradas consultarLiquidacoesRegistradas(
            ControleAcesso controleAcesso, LiquidacoesRegistradasQuery liquidacoesRegistradas) {
        return new ConsultarLiquidacoesRegistradas(controleAcesso, liquidacoesRegistradas);
    }

    /** RAZ-121: registro completo de pagamentos por ente/período. */
    @Bean
    public ConsultarPagamentosRegistrados consultarPagamentosRegistrados(
            ControleAcesso controleAcesso, PagamentosRegistradosQuery pagamentosRegistrados) {
        return new ConsultarPagamentosRegistrados(controleAcesso, pagamentosRegistrados);
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
