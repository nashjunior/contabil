package br.contabil.transparencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.plataforma.application.ConsultarTransparenciaPublica;
import br.contabil.plataforma.application.TotalizarTransparenciaPublica;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.transparencia.CamposOrdenacaoTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.FiltroTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.PaginaTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.PublicacaoTransparencia;
import br.contabil.plataforma.domain.transparencia.TotalizacaoTransparenciaPublica;
import br.contabil.plataforma.infra.transparencia.TransparenciaPayloadPublico;

/** API pública da transparência ativa. Não recebe sessão: o ente vem do path. */
@RestController
@RequestMapping("/transparencia/{enteId}")
final class TransparenciaPublicaController {

    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");
    private static final MediaType TEXT_MARKDOWN = MediaType.parseMediaType("text/markdown; charset=UTF-8");
    private static final CacheControl CACHE_PUBLICO = CacheControl.noCache().cachePublic();

    private final ConsultarTransparenciaPublica consultar;
    private final TotalizarTransparenciaPublica totalizar;
    private final ObjectMapper objectMapper;

    TransparenciaPublicaController(
            ConsultarTransparenciaPublica consultar,
            TotalizarTransparenciaPublica totalizar,
            ObjectMapper objectMapper) {
        this.consultar = consultar;
        this.totalizar = totalizar;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/despesas")
    ResponseEntity<?> despesas(
            @PathVariable("enteId") UUID enteId,
            @RequestParam(value = "estagio", required = false) String estagio,
            @RequestParam(value = "credorId", required = false) String credorId,
            @RequestParam(value = "orgaoId", required = false) String orgaoId,
            @RequestParam(value = "dataInicio", required = false) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) LocalDate dataFim,
            @RequestParam(value = "funcao", required = false) String funcao,
            @RequestParam(value = "numeroEmpenho", required = false) Long numeroEmpenho,
            @RequestParam(value = "contratoId", required = false) String contratoId,
            @RequestParam(value = "ordenarPor", defaultValue = FiltroTransparenciaPublica.ORDENACAO_PADRAO) String ordenarPor,
            @RequestParam(value = "direcao", defaultValue = "desc") String direcao,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "formato", required = false) String formato,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        FiltroTransparenciaPublica filtro = filtro(
                estagio, credorId, orgaoId, dataInicio, dataFim, funcao, numeroEmpenho,
                contratoId, ordenarPor, direcao, limit, cursor);
        PaginaTransparenciaPublica pagina = consultar.executar(new TenantId(enteId), filtro);
        if (csv(formato, accept)) {
            return respostaComCache(csv(pagina), TEXT_CSV);
        }
        return respostaComCache(DetalheResponse.de(pagina, filtro, objectMapper), MediaType.APPLICATION_JSON);
    }

    @GetMapping("/despesas/totalizacoes")
    ResponseEntity<TotalizacaoResponse> totalizacoes(
            @PathVariable("enteId") UUID enteId,
            @RequestParam(value = "estagio", required = false) String estagio,
            @RequestParam(value = "credorId", required = false) String credorId,
            @RequestParam(value = "orgaoId", required = false) String orgaoId,
            @RequestParam(value = "dataInicio", required = false) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) LocalDate dataFim,
            @RequestParam(value = "funcao", required = false) String funcao,
            @RequestParam(value = "numeroEmpenho", required = false) Long numeroEmpenho,
            @RequestParam(value = "contratoId", required = false) String contratoId) {
        FiltroTransparenciaPublica filtro = filtro(
                estagio, credorId, orgaoId, dataInicio, dataFim, funcao, numeroEmpenho,
                contratoId, FiltroTransparenciaPublica.ORDENACAO_PADRAO, "desc", null, null);
        return respostaComCache(
                TotalizacaoResponse.de(totalizar.executar(new TenantId(enteId), filtro), filtro),
                MediaType.APPLICATION_JSON);
    }

    @GetMapping("/despesas/bulk")
    ResponseEntity<BulkManifestResponse> bulk(
            @PathVariable("enteId") UUID enteId,
            @RequestParam(value = "estagio", required = false) String estagio,
            @RequestParam(value = "credorId", required = false) String credorId,
            @RequestParam(value = "orgaoId", required = false) String orgaoId,
            @RequestParam(value = "dataInicio", required = false) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) LocalDate dataFim,
            @RequestParam(value = "funcao", required = false) String funcao,
            @RequestParam(value = "numeroEmpenho", required = false) Long numeroEmpenho,
            @RequestParam(value = "contratoId", required = false) String contratoId) {
        FiltroTransparenciaPublica filtro = filtro(
                estagio, credorId, orgaoId, dataInicio, dataFim, funcao, numeroEmpenho,
                contratoId, FiltroTransparenciaPublica.ORDENACAO_PADRAO, "desc", null, null);
        TotalizacaoTransparenciaPublica totalizacao = totalizar.executar(new TenantId(enteId), filtro);
        String versao = totalizacao.ultimaAtualizacao().map(Instant::toString).orElse("vazio")
                .replace(":", "").replace(".", "");
        return respostaComCache(
                new BulkManifestResponse(
                        "csv",
                        versao,
                        "/cdn/transparencia/%s/despesas/%s.csv".formatted(enteId, versao),
                        totalizacao.contagemAproximada(),
                        totalizacao.ultimaAtualizacao().orElse(null),
                        parametros(filtro)),
                MediaType.APPLICATION_JSON);
    }

    @GetMapping("/dicionario-dados")
    ResponseEntity<String> dicionarioDados() {
        return respostaComCache(DICIONARIO_DADOS, TEXT_MARKDOWN);
    }

    private static FiltroTransparenciaPublica filtro(
            String estagio,
            String credorId,
            String orgaoId,
            LocalDate dataInicio,
            LocalDate dataFim,
            String funcao,
            Long numeroEmpenho,
            String contratoId,
            String ordenarPor,
            String direcao,
            Integer limit,
            String cursor) {
        return new FiltroTransparenciaPublica(
                Optional.ofNullable(estagio),
                Optional.ofNullable(credorId),
                Optional.ofNullable(orgaoId),
                Optional.ofNullable(dataInicio),
                Optional.ofNullable(dataFim),
                Optional.ofNullable(funcao),
                Optional.ofNullable(numeroEmpenho),
                Optional.ofNullable(contratoId),
                ordenarPor,
                FiltroTransparenciaPublica.DirecaoOrdenacao.valueOf(direcao.toUpperCase()),
                limit == null ? FiltroTransparenciaPublica.LIMITE_PADRAO : limit,
                Optional.ofNullable(cursor));
    }

    private static boolean csv(String formato, String accept) {
        return "csv".equalsIgnoreCase(formato) || (accept != null && accept.contains("text/csv"));
    }

    private String csv(PaginaTransparenciaPublica pagina) {
        StringBuilder out = new StringBuilder();
        out.append("sequencia,tipo_evento,recurso,estagio,publicado_em,publicar_ate,data,valor,numero_empenho,credor_id,orgao_id,contrato_id,historico,beneficiario_nome,beneficiario_documento\n");
        for (PublicacaoTransparencia item : pagina.itens()) {
            JsonNode payload = payload(item);
            out.append(item.sequencia()).append(',')
                    .append(csvCampo(item.tipoEvento())).append(',')
                    .append(csvCampo(item.recurso())).append(',')
                    .append(csvCampo(payload.path("estagio").asText())).append(',')
                    .append(item.publicadoEm()).append(',')
                    .append(item.publicarAte()).append(',')
                    .append(csvCampo(primeiroTexto(payload, "dataFato", "dataCompetencia"))).append(',')
                    .append(csvCampo(payload.path("valor").asText())).append(',')
                    .append(csvCampo(payload.path("numeroSequencial").asText())).append(',')
                    .append(csvCampo(payload.path("credorId").asText())).append(',')
                    .append(csvCampo(primeiroTexto(payload, "orgaoId", "unidadeGestoraId"))).append(',')
                    .append(csvCampo(payload.path("contratoId").asText())).append(',')
                    .append(csvCampo(payload.path("historico").asText())).append(',')
                    .append(csvCampo(payload.path("beneficiario").path("nome").asText())).append(',')
                    .append(csvCampo(payload.path("beneficiario").path("documento").asText()))
                    .append('\n');
        }
        return out.toString();
    }

    private JsonNode payload(PublicacaoTransparencia item) {
        return TransparenciaPayloadPublico.sanitizar(objectMapper, item.payloadJson(), item.tipoEvento());
    }

    private static String primeiroTexto(JsonNode payload, String primeiro, String segundo) {
        String valor = payload.path(primeiro).asText(null);
        return valor == null || valor.isBlank() ? payload.path(segundo).asText("") : valor;
    }

    private static String csvCampo(String valor) {
        if (valor == null) {
            return "";
        }
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }

    private static <T> ResponseEntity<T> respostaComCache(T body, MediaType mediaType) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CACHE_PUBLICO)
                .header("X-RateLimit-Limit", "120")
                .header("X-RateLimit-Window-Seconds", "60")
                .body(body);
    }

    private static Map<String, Object> parametros(FiltroTransparenciaPublica filtro) {
        return Map.of(
                "estagio", filtro.estagio().orElse(""),
                "credorId", filtro.credorId().orElse(""),
                "orgaoId", filtro.orgaoId().orElse(""),
                "dataInicio", filtro.dataInicio().map(LocalDate::toString).orElse(""),
                "dataFim", filtro.dataFim().map(LocalDate::toString).orElse(""),
                "funcao", filtro.funcao().orElse(""),
                "numeroEmpenho", filtro.numeroEmpenho().map(Object::toString).orElse(""),
                "contratoId", filtro.contratoId().orElse(""));
    }

    record DetalheResponse(
            List<ItemResponse> itens,
            String proximoCursor,
            boolean haMais,
            long contagemAproximada,
            Instant ultimaAtualizacao,
            Map<String, Object> parametros,
            OrdenacaoResponse ordenacao,
            List<String> camposOrdenaveis) {

        static DetalheResponse de(PaginaTransparenciaPublica pagina, FiltroTransparenciaPublica filtro, ObjectMapper mapper) {
            return new DetalheResponse(
                    pagina.itens().stream().map(item -> ItemResponse.de(item, mapper)).toList(),
                    pagina.proximoCursor().orElse(null),
                    pagina.haMais(),
                    pagina.contagemAproximada(),
                    pagina.ultimaAtualizacao().orElse(null),
                    TransparenciaPublicaController.parametros(filtro),
                    new OrdenacaoResponse(filtro.ordenarPor(), filtro.direcao().name().toLowerCase(), "mais recente primeiro por data de publicação"),
                    CamposOrdenacaoTransparenciaPublica.todos().stream().sorted().toList());
        }
    }

    record ItemResponse(
            String tipoEvento,
            String recurso,
            long sequencia,
            Instant publicadoEm,
            Instant publicarAte,
            JsonNode payload) {

        static ItemResponse de(PublicacaoTransparencia item, ObjectMapper mapper) {
            return new ItemResponse(
                    item.tipoEvento(),
                    item.recurso(),
                    item.sequencia(),
                    item.publicadoEm(),
                    item.publicarAte(),
                    TransparenciaPayloadPublico.sanitizar(mapper, item.payloadJson(), item.tipoEvento()));
        }
    }

    record OrdenacaoResponse(String campo, String direcao, String padraoDocumentado) {}

    record TotalizacaoResponse(
            List<TotalizacaoTransparenciaPublica.Linha> linhas,
            BigDecimal totalGeral,
            long contagemAproximada,
            Instant ultimaAtualizacao,
            Map<String, Object> parametros) {

        static TotalizacaoResponse de(TotalizacaoTransparenciaPublica totalizacao, FiltroTransparenciaPublica filtro) {
            return new TotalizacaoResponse(
                    totalizacao.linhas(),
                    totalizacao.totalGeral(),
                    totalizacao.contagemAproximada(),
                    totalizacao.ultimaAtualizacao().orElse(null),
                    TransparenciaPublicaController.parametros(filtro));
        }
    }

    record BulkManifestResponse(
            String formato,
            String versao,
            String arquivoCdn,
            long contagemAproximada,
            Instant ultimaAtualizacao,
            Map<String, Object> parametros) {}

    private static final String DICIONARIO_DADOS =
            """
            # Dicionário de dados da transparência ativa

            A API lê somente o read model público `transparencia_publicacao`.

            ## Campos principais

            - `estagio`: etapa estável da execução. Exemplos: `empenhado`, `liquidado`, `pago`.
            - `valor`: valor monetário decimal com duas casas. Exemplo: `1520.35`.
            - `data`: data do fato ou competência. Exemplo: `2026-08-01`.
            - `credorId`: identificador público do credor no ente.
            - `orgaoId`: órgão ou unidade gestora responsável.
            - `numeroEmpenho`: número sequencial do empenho.
            - `contratoId`: contrato relacionado, quando houver.
            - `beneficiario.documento`: CPF mascarado como `***.456.***-**`; CNPJ pode aparecer inteiro.

            RG, endereço e conta bancária não são publicados. Remuneração nominal e CNPJ são permitidos quando existirem fonte legal e estruturante próprio.

            ## Filtros

            Detalhe e totalização aceitam os mesmos parâmetros: `estagio`, `credorId`, `orgaoId`, `dataInicio`, `dataFim`, `funcao`, `numeroEmpenho`, `contratoId`.

            ## Ordenação

            O padrão é `ordenarPor=publicadoEm&direcao=desc`, ou seja, mais recente primeiro por data de publicação. Campos ordenáveis: `publicadoEm`, `data`, `valor`, `numeroEmpenho`.

            ## Paginação

            A paginação é keyset por `cursor`. A resposta informa `haMais`, `proximoCursor` e `contagemAproximada`.
            """;
}
