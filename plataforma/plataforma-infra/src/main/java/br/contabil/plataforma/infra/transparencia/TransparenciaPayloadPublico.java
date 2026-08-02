package br.contabil.plataforma.infra.transparencia;

import java.text.Normalizer;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Barreira defensiva para todos os canais públicos da transparência.
 *
 * <p>O produtor já deve enviar payload minimizado/mascarado; esta classe impede
 * regressão acidental no read model, JSON, CSV e bulk.
 */
public final class TransparenciaPayloadPublico {

    private static final Pattern CPF_PONTUADO =
            Pattern.compile("(?<!\\d)(\\d{3})\\.(\\d{3})\\.(\\d{3})-(\\d{2})(?!\\d)");
    private static final Pattern CPF_NUMERICO = Pattern.compile("(?<!\\d)(\\d{11})(?!\\d)");

    private TransparenciaPayloadPublico() {}

    public static ObjectNode sanitizar(ObjectMapper objectMapper, String payloadJson, String tipoEvento) {
        try {
            JsonNode raiz = objectMapper.readTree(payloadJson);
            ObjectNode objeto = raiz != null && raiz.isObject() ? (ObjectNode) raiz.deepCopy() : objectMapper.createObjectNode();
            sanitizarRecursivo(objeto);
            if (!objeto.hasNonNull("estagio")) {
                objeto.put("estagio", estagioDe(tipoEvento, objeto.path("evento").asText()));
            }
            objeto.put("tipoEvento", tipoEvento);
            return objeto;
        } catch (Exception ex) {
            throw new IllegalArgumentException("payload público da transparência não é JSON válido", ex);
        }
    }

    public static String estagioDe(String tipoEvento, String evento) {
        String valor = (tipoEvento == null || tipoEvento.isBlank()) ? evento : tipoEvento;
        if (valor == null) {
            return "desconhecido";
        }
        if (valor.contains("empenho") || valor.equalsIgnoreCase("empenho")) {
            return "empenhado";
        }
        if (valor.contains("liquidacao") || valor.equalsIgnoreCase("liquidacao")) {
            return "liquidado";
        }
        if (valor.contains("pagamento") || valor.equalsIgnoreCase("pagamento")) {
            return "pago";
        }
        if (valor.contains("receita")) {
            return "receita";
        }
        return "desconhecido";
    }

    private static void sanitizarRecursivo(JsonNode node) {
        if (node instanceof ObjectNode objeto) {
            Iterator<Map.Entry<String, JsonNode>> campos = objeto.fields();
            while (campos.hasNext()) {
                Map.Entry<String, JsonNode> campo = campos.next();
                if (deveSuprimir(campo.getKey())) {
                    campos.remove();
                    continue;
                }
                JsonNode valor = campo.getValue();
                if (valor.isTextual()) {
                    objeto.set(campo.getKey(), TextNode.valueOf(maskCpf(valor.asText())));
                } else {
                    sanitizarRecursivo(valor);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode item : array) {
                sanitizarRecursivo(item);
            }
        }
    }

    private static boolean deveSuprimir(String nome) {
        String normalizado = normalizar(nome);
        return normalizado.equals("rg")
                || normalizado.contains("registrogeral")
                || normalizado.contains("endereco")
                || normalizado.contains("logradouro")
                || normalizado.contains("bairro")
                || normalizado.equals("cep")
                || normalizado.contains("contabancaria")
                || normalizado.contains("agencia")
                || normalizado.equals("banco")
                || normalizado.contains("iban")
                || normalizado.contains("chavepix");
    }

    private static String maskCpf(String valor) {
        Matcher pontuado = CPF_PONTUADO.matcher(valor);
        String mascarado = pontuado.replaceAll("***.$2.***-**");
        Matcher numerico = CPF_NUMERICO.matcher(mascarado);
        StringBuffer buffer = new StringBuffer();
        while (numerico.find()) {
            String digitos = numerico.group(1);
            numerico.appendReplacement(buffer, "***." + digitos.substring(3, 6) + ".***-**");
        }
        numerico.appendTail(buffer);
        return buffer.toString();
    }

    private static String normalizar(String valor) {
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
