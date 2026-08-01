package br.contabil.razao.domain.repository;

import java.util.Optional;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.PeriodoContabil;

/**
 * Port de escrita do ciclo de vida do período contábil (RAZ-206, ADR-0045).
 * Distinto de {@link PeriodoContabilPort} (só leitura por competência, usado
 * na escrituração de fatos, que rejeita período fechado/inexistente) — este
 * port serve o rito de encerramento, que precisa ler o estado atual e
 * transicioná-lo.
 */
public interface PeriodoContabilRepository {

    Optional<PeriodoContabil> buscarPorCompetencia(TenantId enteId, int exercicio, int mes);

    /**
     * Persiste a transição para {@code ENCERRADO}. Condicional: só grava se a
     * linha ainda está {@code aberto} (guarda de concorrência — ADR-0045,
     * mesma mecânica do {@code AprovarPagamento}/ADR-0023). Retorna
     * {@code true} se esta chamada venceu a transição; {@code false} se outra
     * já encerrou o período entre a leitura e a escrita — o chamador mapeia
     * isso a {@link br.contabil.razao.domain.PeriodoJaEncerradoException},
     * nunca sobrescreve.
     */
    boolean encerrar(PeriodoContabil periodo);
}
