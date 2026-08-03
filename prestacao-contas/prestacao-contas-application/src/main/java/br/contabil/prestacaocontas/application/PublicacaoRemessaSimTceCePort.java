package br.contabil.prestacaocontas.application;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;
import br.contabil.prestacaocontas.domain.RemessaSimTceCe;

/** Porta de publicação da remessa SIM/TCE-CE via entrega garantida. */
public interface PublicacaoRemessaSimTceCePort {

    IdEntrega publicar(RemessaSimTceCe remessa, ChaveIdempotencia chave);
}
