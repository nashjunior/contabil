package br.contabil.prestacaocontas.infra;

import java.time.LocalDate;
import java.util.Objects;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.entrega.ServicoEntrega;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.MensagemEntrega;
import br.contabil.prestacaocontas.application.PublicacaoRemessaSimTceCePort;
import br.contabil.prestacaocontas.domain.RemessaSimTceCe;

/** Publicação assíncrona do pacote SIMWEB via outbox idempotente. */
public final class PublicacaoRemessaSimTceCeOutboxAdapter implements PublicacaoRemessaSimTceCePort {

    private static final String DESTINO_SIMWEB = "tce-ce-simweb";
    private static final String TIPO_EVENTO = "prestacao_contas.sim_tce_ce.remessa.v1";

    private final ServicoEntrega entrega;

    public PublicacaoRemessaSimTceCeOutboxAdapter(ServicoEntrega entrega) {
        this.entrega = Objects.requireNonNull(entrega, "entrega");
    }

    @Override
    public IdEntrega publicar(RemessaSimTceCe remessa, ChaveIdempotencia chave) {
        Objects.requireNonNull(remessa, "remessa");
        Objects.requireNonNull(chave, "chave");
        String payload = payload(remessa);
        return entrega.enqueue(new MensagemEntrega(remessa.enteId(), DESTINO_SIMWEB, TIPO_EVENTO, payload), chave);
    }

    private String payload(RemessaSimTceCe remessa) {
        return objetoJson(
                campo("evento", "remessa_sim_tce_ce"),
                campo("enteId", remessa.enteId().valor().toString()),
                campo("tabela", remessa.tabela()),
                campo("exercicio", Integer.toString(remessa.exercicio())),
                campo("mes", Integer.toString(remessa.mes())),
                campo("prazo", prazo(remessa).toString()),
                campo("nomeArquivoPgi", remessa.nomeArquivoPgi()),
                campo("nomeArquivoZip", remessa.nomeArquivoZip()),
                campo("conteudoZipBase64", remessa.conteudoZipBase64()));
    }

    private LocalDate prazo(RemessaSimTceCe remessa) {
        return LocalDate.of(remessa.exercicio(), remessa.mes(), 1).plusMonths(1).withDayOfMonth(30);
    }

    private static String objetoJson(String... campos) {
        return "{" + String.join(",", campos) + "}";
    }

    private static String campo(String nome, String valor) {
        return "\"" + escapar(nome) + "\":\"" + escapar(valor) + "\"";
    }

    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
