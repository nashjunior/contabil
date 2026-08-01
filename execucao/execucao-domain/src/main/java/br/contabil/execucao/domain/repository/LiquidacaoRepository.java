package br.contabil.execucao.domain.repository;

import java.util.Optional;

import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.plataforma.domain.TenantId;

/** Port de persistência de liquidações. */
public interface LiquidacaoRepository {

    void inserir(Liquidacao liquidacao);

    Optional<Liquidacao> buscarPorId(TenantId enteId, LiquidacaoId id);

    /**
     * Persiste a decisão de aprovação (ADR-0023: {@code aprovada}/{@code devolvida} é estado
     * forte, transicionado só por {@code AprovarPagamento}) — não é um update de negócio livre,
     * é a única mutação permitida sobre a liquidação já registrada.
     *
     * <p>Transição <b>condicional</b>: só atualiza a linha que ainda está {@code pendente}
     * (guarda de concorrência do gate 4-eyes — mecânica do ADR-0027 R3, aplicada à liquidação).
     * Retorna {@code true} se esta decisão ganhou a transição; {@code false} se outra decisão
     * concorrente já saiu de {@code pendente} entre a leitura e a escrita — o chamador deve mapear
     * isso a {@code liquidacao_ja_decidida} (409, ADR-0029 §4), nunca sobrescrever (last-write-wins).
     */
    boolean atualizarDecisaoAprovacao(Liquidacao liquidacao);
}
