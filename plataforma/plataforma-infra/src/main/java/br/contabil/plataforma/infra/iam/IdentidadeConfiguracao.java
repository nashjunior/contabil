package br.contabil.plataforma.infra.iam;

import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de IAM (ADR-0002 / AGENTS.md): {@link ServicoIdentidade} é o adapter
 * gov.br/ICP-Brasil — hoje {@link ServicoIdentidadeIndisponivel} (fail-closed,
 * RAZ-5 ainda não entregue); {@link ControleAcesso} é o serviço de domínio
 * (RAZ-33) que os use cases protegidos consomem.
 */
@Configuration
public class IdentidadeConfiguracao {

    @Bean
    public ServicoIdentidade servicoIdentidade() {
        return new ServicoIdentidadeIndisponivel();
    }

    @Bean
    public ControleAcesso controleAcesso(ServicoIdentidade servicoIdentidade) {
        return new ControleAcesso(servicoIdentidade);
    }
}
