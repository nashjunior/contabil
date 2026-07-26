package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.ErroContrato;

/** Erro de negócio estável dos agregados de execução da despesa. */
public class ExecucaoInvalidaException extends RuntimeException implements ErroContrato {

    private final String codigo;

    public ExecucaoInvalidaException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    @Override
    public String codigo() {
        return codigo;
    }
}
