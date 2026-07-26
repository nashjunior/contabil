package br.contabil.execucao.infra.documento;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import br.contabil.execucao.application.GerarDocumentoEmpenho;
import br.contabil.execucao.domain.GeradorNotaEmpenhoPdf;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;

/**
 * Wiring do worker de geração do documento pendente (ADR-0027 (b)). Mesmo
 * pré-requisito de {@code ObjectStoreSeamsAssinaturaConfiguration}: desligado
 * por padrão, liga com {@code contabil.objectstore.enabled=true} — sem object
 * store não há onde publicar o PDF/A, então o caso de uso e o worker nem
 * existem no contexto (fail-fast na composição, nunca fail-open).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(EmpenhoDocumentoPendenteProperties.class)
@ConditionalOnProperty(prefix = "contabil.objectstore", name = "enabled", havingValue = "true")
@ConditionalOnBean(ArmazenamentoDocumentos.class)
public class EmpenhoDocumentoPendenteConfiguration {

    @Bean
    public GerarDocumentoEmpenho gerarDocumentoEmpenho(
            EmpenhoRepository repositorio,
            GeradorNotaEmpenhoPdf gerador,
            ArmazenamentoDocumentos armazenamento,
            EmpenhoDocumentoPendenteProperties properties,
            AuditoriaEscrita auditoria,
            Clock clock) {
        return new GerarDocumentoEmpenho(
                repositorio, gerador, armazenamento, properties.getBucket(), auditoria, clock);
    }

    @Bean
    public EmpenhoDocumentoPendenteWorker empenhoDocumentoPendenteWorker(
            EmpenhoDocumentoOutboxRepository repository,
            GerarDocumentoEmpenho gerarDocumentoEmpenho,
            EmpenhoDocumentoPendenteProperties properties,
            Clock clock) {
        return new EmpenhoDocumentoPendenteWorker(repository, gerarDocumentoEmpenho, properties, clock);
    }

    @Bean
    EmpenhoDocumentoPendenteScheduler empenhoDocumentoPendenteScheduler(
            EmpenhoDocumentoPendenteWorker worker, EmpenhoDocumentoPendenteProperties properties) {
        return new EmpenhoDocumentoPendenteScheduler(worker, properties);
    }
}
