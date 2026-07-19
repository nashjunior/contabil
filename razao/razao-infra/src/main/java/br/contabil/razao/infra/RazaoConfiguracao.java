package br.contabil.razao.infra;

import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.razao.application.EstornarFatoContabil;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.FatoContabilRepository;
import br.contabil.razao.domain.repository.PeriodoContabilPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
