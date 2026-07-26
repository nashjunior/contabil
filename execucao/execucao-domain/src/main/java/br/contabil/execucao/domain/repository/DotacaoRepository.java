package br.contabil.execucao.domain.repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import br.contabil.execucao.domain.CreditoAdicional;
import br.contabil.execucao.domain.Dotacao;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/** Port de persistência da dotação (Fixação/carga da LOA — RAZ-80; ingestão em lote — RAZ-89). */
public interface DotacaoRepository {

    void inserir(Dotacao dotacao);

    Optional<Dotacao> buscarPorId(TenantId enteId, DotacaoId id);

    /**
     * Insere um lote de dotações (Fixação — carga da LOA), fail-soft (ADR-0013): reentrega do
     * mesmo {@code id} (retry idempotente) não derruba o lote, aparece em {@code erros} sem
     * interromper as demais inserções.
     */
    ResultadoLote<DotacaoId> inserirEmLote(List<Dotacao> dotacoes);

    /**
     * Aplica um lote de créditos adicionais (Lei 4.320/1964 arts. 40-46), fail-soft (ADR-0013):
     * soma atômica em {@code valor_autorizado} (sem carregar o agregado); crédito para dotação
     * inexistente aparece em {@code erros} sem interromper os demais.
     */
    ResultadoLote<DotacaoId> aplicarCreditosEmLote(TenantId enteId, List<CreditoAdicional> creditos);

    /** Resultado de uma operação em lote fail-soft: os itens processados e os que falharam. */
    record ResultadoLote<T>(List<T> processados, List<ErroItemLote> erros) {
        public ResultadoLote {
            Validacoes.exigirNaoNulo(processados, "processados");
            Validacoes.exigirNaoNulo(erros, "erros");
            processados = List.copyOf(processados);
            erros = List.copyOf(erros);
        }
    }

    /** Falha de um item do lote: referência do item (não o objeto inteiro) + motivo. */
    record ErroItemLote(String referencia, String motivo) {
        public ErroItemLote {
            Objects.requireNonNull(referencia, "referência não pode ser nula");
            Validacoes.exigirNaoNulo(motivo, "motivo");
        }
    }
}
