package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * Erro {@code empenho_nao_encontrado}: leitura por id (ADR-0039 decisão 1,
 * ratificada em RAZ-152/§6.10, RAZ-156) não achou o empenho para o {@code
 * enteId} do path — id inexistente ou de outro ente (RLS já escopa a consulta
 * antes de chegar aqui).
 */
public final class EmpenhoNaoEncontradoException extends RuntimeException implements ErroContrato {

    public EmpenhoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    @Override
    public String codigo() {
        return "empenho_nao_encontrado";
    }
}
