package br.contabil.execucao.infra;

import br.contabil.execucao.application.PublicacaoTransparenciaExecucaoPort;
import br.contabil.execucao.application.PublicadorTransparenciaExecucao;
import br.contabil.execucao.application.RegistrarEmpenho;
import br.contabil.execucao.application.SinalizacaoSlaTransparenciaPort;
import br.contabil.execucao.domain.repository.ContadorEmpenhoPort;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.execucao.domain.repository.ExecucaoContabilPort;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.entrega.ServicoEntrega;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new RegistrarEmpenho(
                controleAcesso,
                saldos,
                escrituracao,
                contadorEmpenho,
                repositorio,
                publicacaoTransparencia,
                auditoria,
                clock);
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
