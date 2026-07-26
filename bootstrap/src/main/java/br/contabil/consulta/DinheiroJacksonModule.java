package br.contabil.consulta;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Serializa todo {@link Dinheiro} como <b>string decimal de 2 casas</b>
 * ({@code "128450.00"}) em toda borda HTTP JSON — enforcement de RAZ-79 §6.1 /
 * {@code ADR-0006} / {@code ADR-0030 §2}: dinheiro na leitura é string, nunca
 * número JSON, para o cliente HTTP jamais desserializar o valor como ponto
 * flutuante (o hazard é de leitura — quem parseia é o cliente).
 *
 * <p>Registrado <b>uma única vez</b> (é um {@code @Component} do tipo
 * {@link SimpleModule}, coletado pelo {@code ObjectMapper} auto-configurado do
 * Spring Boot); os DTOs de resposta passam a carregar {@link Dinheiro} em vez de
 * desembrulhar para {@code BigDecimal} cru, centralizando a garantia em todas as
 * bordas — presentes e futuras — sem repetir {@code toPlainString()} em cada DTO.
 * A escala 2 é garantida pelo próprio {@link Dinheiro} (invariante de construção).
 */
@Component
public final class DinheiroJacksonModule extends SimpleModule {

    public DinheiroJacksonModule() {
        super("DinheiroJacksonModule");
        addSerializer(Dinheiro.class, new DinheiroJsonSerializer());
    }

    private static final class DinheiroJsonSerializer extends JsonSerializer<Dinheiro> {

        @Override
        public void serialize(Dinheiro dinheiro, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(dinheiro.valor().toPlainString());
        }
    }
}
