package br.contabil.plataforma.infra.transparencia;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.contabil.plataforma.infra.entrega.BrokerEntrega;
import br.contabil.plataforma.infra.entrega.FalhaPermanenteEntregaException;
import br.contabil.plataforma.infra.entrega.MensagemOutbox;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

final class PostgresTransparenciaBrokerEntrega implements BrokerEntrega {

    private static final String DESTINO_TRANSPARENCIA = "transparencia";
    private static final String METRICA_SLA = "siafic.publicacao.transparencia.sla";
    private static final String SQL_INSERT =
            """
            select transparencia_publicacao_inserir(?, ?, ?, ?, ?, ?::jsonb, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    PostgresTransparenciaBrokerEntrega(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock, MeterRegistry meterRegistry) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void publicar(MensagemOutbox mensagem) {
        Objects.requireNonNull(mensagem, "mensagem");
        if (!DESTINO_TRANSPARENCIA.equals(mensagem.destino())) {
            throw new FalhaPermanenteEntregaException("destino de entrega sem broker real: " + mensagem.destino());
        }

        ObjectNode payload = TransparenciaPayloadPublico.sanitizar(objectMapper, mensagem.conteudo(), mensagem.tipo());
        Instant publicarAte = instantObrigatorio(payload, "publicarAte");
        Instant publicadoEm = clock.instant();
        if (publicadoEm.isAfter(publicarAte)) {
            registrarSla("atrasado");
            throw new FalhaPermanenteEntregaException(
                    "publicação da transparência fora do SLA: publicado_em=%s publicar_ate=%s"
                            .formatted(publicadoEm, publicarAte));
        }

        jdbcTemplate.update(
                SQL_INSERT,
                mensagem.ente().valor(),
                mensagem.tipo(),
                recursoDe(mensagem.tipo(), payload),
                publicadoEm,
                publicarAte,
                payload.toString(),
                mensagem.chave().valor());
        registrarSla("dentro_do_prazo");
    }

    private static Instant instantObrigatorio(JsonNode payload, String campo) {
        String valor = payload.path(campo).asText(null);
        if (valor == null || valor.isBlank()) {
            throw new FalhaPermanenteEntregaException("payload da transparência sem campo obrigatório: " + campo);
        }
        return Instant.parse(valor);
    }

    private static String recursoDe(String tipoEvento, JsonNode payload) {
        String estagio = TransparenciaPayloadPublico.estagioDe(tipoEvento, payload.path("evento").asText());
        return switch (estagio) {
            case "empenhado" -> "execucao:empenho:" + textoObrigatorio(payload, "empenhoId");
            case "liquidado" -> "execucao:liquidacao:" + textoObrigatorio(payload, "liquidacaoId");
            case "pago" -> "execucao:pagamento:" + textoObrigatorio(payload, "pagamentoId");
            default -> tipoEvento + ":" + textoObrigatorio(payload, "fatoContabilId");
        };
    }

    private static String textoObrigatorio(JsonNode payload, String campo) {
        String valor = payload.path(campo).asText(null);
        if (valor == null || valor.isBlank()) {
            throw new FalhaPermanenteEntregaException("payload da transparência sem campo obrigatório: " + campo);
        }
        return valor;
    }

    private void registrarSla(String resultado) {
        Counter.builder(METRICA_SLA)
                .description("Resultado do SLA de publicação da transparência ativa")
                .tag("resultado", resultado)
                .register(meterRegistry)
                .increment();
    }
}
