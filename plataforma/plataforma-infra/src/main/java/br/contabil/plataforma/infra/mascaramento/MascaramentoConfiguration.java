package br.contabil.plataforma.infra.mascaramento;

import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramentoComAuditoria;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramentoPadrao;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root da biblioteca única de mascaramento LGPD. */
@Configuration(proxyBeanMethods = false)
public class MascaramentoConfiguration {

    @Bean
    public ServicoMascaramento servicoMascaramento(AuditoriaEscrita auditoria, Clock clock) {
        return new ServicoMascaramentoComAuditoria(new ServicoMascaramentoPadrao(), auditoria, clock);
    }
}
