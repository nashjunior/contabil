package br.contabil.execucao.domain.repository;

import java.util.Optional;

import br.contabil.execucao.domain.FiltroFilaAprovacao;
import br.contabil.execucao.domain.PaginaFilaAprovacao;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

/**
 * Read model da fila de aprovação (ADR-0007, ADR-0029 §1) — <b>separado</b> do
 * {@code LiquidacaoRepository}, que permanece write-only. A fila é uma query
 * dedicada por status + filtro, nunca {@code buscarPorId} em laço.
 *
 * <p>{@code solicitante} é a identidade da sessão: a implementação DEVE excluir,
 * <b>no SQL/servidor</b>, toda liquidação cujo autor (da própria liquidação
 * <b>ou</b> do empenho da cadeia) seja esse CPF — a mesma segregação da Regra 9
 * que {@code AutoAprovacaoNaoPermitidaException} barra na escrita, antecipada na
 * leitura (ADR-0029 §2, defense-in-depth). Não é filtro opcional do cliente: é
 * invariante de segurança.
 */
public interface FilaAprovacaoQuery {

    /**
     * @param limite página máxima já validada pelo use case (1..100)
     * @param cursor cursor opaco da página anterior (vazio = primeira página)
     */
    PaginaFilaAprovacao consultar(
            TenantId enteId, Cpf solicitante, FiltroFilaAprovacao filtro, int limite, Optional<String> cursor);
}
