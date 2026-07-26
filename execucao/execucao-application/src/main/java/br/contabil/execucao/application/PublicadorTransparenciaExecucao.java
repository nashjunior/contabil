package br.contabil.execucao.application;

import br.contabil.execucao.domain.Beneficiario;
import br.contabil.execucao.domain.DocumentoSuporte;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.Pagamento;
import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.MensagemEntrega;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.Audiencia;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.BaseLegalLgpd;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.CampoSensivel;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.Categoria;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.ContextoAcesso;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Publicador de eventos de execução para a transparência ativa.
 *
 * <p>Produz payload público já minimizado/mascarado e enfileira via {@link ServicoEntrega}.
 * O worker/outbox faz a borda externa; este componente só grava a intenção idempotente de
 * publicar na mesma transação do registro da execução.
 */
public final class PublicadorTransparenciaExecucao implements PublicacaoTransparenciaExecucaoPort {

    private static final String DESTINO_TRANSPARENCIA = "transparencia";
    private static final String FINALIDADE = "transparencia_ativa_execucao";
    private static final String SOLICITANTE_PUBLICO = "portal-publico";

    private final ServicoEntrega entrega;
    private final ServicoMascaramento mascaramento;
    private final SinalizacaoSlaTransparenciaPort sinalizacaoSla;
    private final Clock clock;

    public PublicadorTransparenciaExecucao(
            ServicoEntrega entrega,
            ServicoMascaramento mascaramento,
            SinalizacaoSlaTransparenciaPort sinalizacaoSla,
            Clock clock) {
        this.entrega = Objects.requireNonNull(entrega, "serviço de entrega");
        this.mascaramento = Objects.requireNonNull(mascaramento, "serviço de mascaramento");
        this.sinalizacaoSla = Objects.requireNonNull(sinalizacaoSla, "sinalização de SLA");
        this.clock = Objects.requireNonNull(clock, "relógio");
    }

    @Override
    public void publicar(Empenho empenho, Sessao sessao) {
        Objects.requireNonNull(empenho, "empenho");
        Objects.requireNonNull(sessao, "sessão");

        String tipoEvento = "execucao.empenho.registrado.v1";
        Instant registradoEm = Instant.now(clock);
        Instant publicarAte = primeiroDiaUtilSubsequente(registradoEm);
        String recurso = "execucao:empenho:%s".formatted(empenho.id().valor());
        String payload = payloadEmpenho(empenho, registradoEm, publicarAte);
        IdEntrega entregaId = entrega.enqueue(
                new MensagemEntrega(empenho.enteId(), DESTINO_TRANSPARENCIA, tipoEvento, payload),
                chave(empenho.enteId(), "empenho", empenho.id().valor().toString()));

        sinalizar(empenho.enteId(), tipoEvento, recurso, entregaId, registradoEm, publicarAte);
    }

    @Override
    public void publicar(Liquidacao liquidacao, Sessao sessao) {
        Objects.requireNonNull(liquidacao, "liquidação");
        Objects.requireNonNull(sessao, "sessão");

        String tipoEvento = "execucao.liquidacao.registrada.v1";
        Instant registradoEm = Instant.now(clock);
        Instant publicarAte = primeiroDiaUtilSubsequente(registradoEm);
        String recurso = "execucao:liquidacao:%s".formatted(liquidacao.id().valor());
        String payload = payloadLiquidacao(liquidacao, registradoEm, publicarAte);
        IdEntrega entregaId = entrega.enqueue(
                new MensagemEntrega(liquidacao.enteId(), DESTINO_TRANSPARENCIA, tipoEvento, payload),
                chave(liquidacao.enteId(), "liquidacao", liquidacao.id().valor().toString()));

        sinalizar(liquidacao.enteId(), tipoEvento, recurso, entregaId, registradoEm, publicarAte);
    }

    @Override
    public void publicar(Pagamento pagamento, Sessao sessao) {
        Objects.requireNonNull(pagamento, "pagamento");
        Objects.requireNonNull(sessao, "sessão");

        String tipoEvento = "execucao.pagamento.registrado.v1";
        Instant registradoEm = Instant.now(clock);
        Instant publicarAte = primeiroDiaUtilSubsequente(registradoEm);
        String recurso = "execucao:pagamento:%s".formatted(pagamento.id().valor());
        ContextoAcesso contexto = contextoPublico(pagamento.enteId());
        String payload = payloadPagamento(pagamento, contexto, registradoEm, publicarAte);
        IdEntrega entregaId = entrega.enqueue(
                new MensagemEntrega(pagamento.enteId(), DESTINO_TRANSPARENCIA, tipoEvento, payload),
                chave(pagamento.enteId(), "pagamento", pagamento.id().valor().toString()));

        sinalizar(pagamento.enteId(), tipoEvento, recurso, entregaId, registradoEm, publicarAte);
    }

    private String payloadEmpenho(Empenho empenho, Instant registradoEm, Instant publicarAte) {
        return objetoJson(
                campo("evento", "empenho"),
                campo("enteId", empenho.enteId().valor().toString()),
                campo("empenhoId", empenho.id().valor().toString()),
                campo("numeroSequencial", Long.toString(empenho.numeroSequencial())),
                campo("exercicio", Integer.toString(empenho.exercicio())),
                campo("tipo", empenho.tipo().codigo()),
                campo("dotacaoId", empenho.dotacaoId().valor().toString()),
                campo("credorId", empenho.credorId().valor().toString()),
                campo("unidadeGestoraId", empenho.unidadeGestoraId().valor().toString()),
                campoCru("contratoId", campoOpcional(empenho.contratoId() == null ? null : empenho.contratoId().valor())),
                campo("fatoContabilId", empenho.fatoContabilId().valor().toString()),
                campo("dataFato", empenho.dataFato().toString()),
                campo("classificacaoOrcamentaria", empenho.classificacaoOrcamentaria()),
                campo("fonteRecurso", empenho.fonteRecurso()),
                campo("valor", empenho.valor().valor().toPlainString()),
                campo("historico", empenho.historico()),
                campo("registradoEm", registradoEm.toString()),
                campo("publicarAte", publicarAte.toString()));
    }

    private String payloadLiquidacao(Liquidacao liquidacao, Instant registradoEm, Instant publicarAte) {
        return objetoJson(
                campo("evento", "liquidacao"),
                campo("enteId", liquidacao.enteId().valor().toString()),
                campo("liquidacaoId", liquidacao.id().valor().toString()),
                campo("empenhoId", liquidacao.empenhoId().valor().toString()),
                campo("fatoContabilId", liquidacao.fatoContabilId().toString()),
                campo("dataCompetencia", liquidacao.dataCompetencia().toString()),
                campo("valor", liquidacao.valor().valor().toPlainString()),
                campo("historico", liquidacao.historico()),
                campo("registradoEm", registradoEm.toString()),
                campo("publicarAte", publicarAte.toString()),
                campoCru("documentosSuporte", documentosJson(liquidacao)));
    }

    private String payloadPagamento(Pagamento pagamento, ContextoAcesso contexto, Instant registradoEm, Instant publicarAte) {
        Optional<String> beneficiarioJson = pagamento.beneficiario().map(beneficiario -> campoCru(
                "beneficiario",
                objetoJson(
                        campo("nome", beneficiario.nome()),
                        campo("documento", documentoBeneficiarioPublico(beneficiario, contexto)))));

        return objetoJson(
                campo("evento", "pagamento"),
                campo("enteId", pagamento.enteId().valor().toString()),
                campo("pagamentoId", pagamento.id().valor().toString()),
                campo("liquidacaoId", pagamento.liquidacaoId().valor().toString()),
                campo("fatoContabilId", pagamento.fatoContabilId().toString()),
                campo("dataCompetencia", pagamento.dataCompetencia().toString()),
                campo("valor", pagamento.valor().valor().toPlainString()),
                campo("natureza", pagamento.natureza().name()),
                campo("historico", pagamento.historico()),
                campo("registradoEm", registradoEm.toString()),
                campo("publicarAte", publicarAte.toString()),
                beneficiarioJson.orElse(campoCru("beneficiario", "null")));
    }

    private String documentosJson(Liquidacao liquidacao) {
        StringJoiner itens = new StringJoiner(",", "[", "]");
        for (DocumentoSuporte documento : liquidacao.documentosSuporte()) {
            itens.add(objetoJson(
                    campo("tipo", documento.tipo()),
                    campo("numero", documento.numero()),
                    campo("dataEmissao", documento.dataEmissao().toString()),
                    documento.referenciaExterna()
                            .map(referencia -> campo("referenciaExterna", referencia))
                            .orElse(campoCru("referenciaExterna", "null"))));
        }
        return itens.toString();
    }

    private String documentoBeneficiarioPublico(Beneficiario beneficiario, ContextoAcesso contexto) {
        String documento = beneficiario.cpfCnpj();
        String digitos = documento.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            return mascaramento.mascarar(new CampoSensivel("beneficiario.cpf", documento, Categoria.CPF),
                    contexto, Audiencia.PORTAL_PUBLICO);
        }
        if (digitos.length() == 14) {
            return documento;
        }
        return mascaramento.mascarar(new CampoSensivel("beneficiario.documento", documento, Categoria.OUTRO),
                contexto, Audiencia.PORTAL_PUBLICO);
    }

    private ContextoAcesso contextoPublico(TenantId enteId) {
        return new ContextoAcesso(enteId, SOLICITANTE_PUBLICO, FINALIDADE, BaseLegalLgpd.OBRIGACAO_LEGAL);
    }

    private void sinalizar(
            TenantId enteId,
            String tipoEvento,
            String recurso,
            IdEntrega entregaId,
            Instant registradoEm,
            Instant publicarAte) {
        SinalizacaoSlaTransparenciaPort.ResultadoSla resultado =
                !Instant.now(clock).isAfter(publicarAte)
                        ? SinalizacaoSlaTransparenciaPort.ResultadoSla.DENTRO_DO_PRAZO
                        : SinalizacaoSlaTransparenciaPort.ResultadoSla.ATRASADO;
        sinalizacaoSla.registrar(new SinalizacaoSlaTransparenciaPort.SinalPublicacao(
                enteId, tipoEvento, recurso, entregaId, registradoEm, publicarAte, resultado));
    }

    private Instant primeiroDiaUtilSubsequente(Instant registradoEm) {
        ZoneId zone = clock.getZone();
        LocalDate dia = LocalDate.ofInstant(registradoEm, zone).plusDays(1);
        while (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(1);
        }
        return dia.atTime(LocalTime.MAX).atZone(zone).toInstant();
    }

    private ChaveIdempotencia chave(TenantId enteId, String tipo, String id) {
        return ChaveIdempotencia.de("transparencia:execucao:%s:%s:%s".formatted(tipo, enteId.valor(), id));
    }

    private static String campoOpcional(Object valor) {
        return valor == null ? "null" : "\"" + escapar(valor.toString()) + "\"";
    }

    private static String objetoJson(String... campos) {
        return "{" + String.join(",", campos) + "}";
    }

    private static String campo(String nome, String valor) {
        return campoCru(nome, "\"" + escapar(valor) + "\"");
    }

    private static String campoCru(String nome, String valorJson) {
        return "\"" + escapar(nome) + "\":" + valorJson;
    }

    private static String escapar(String valor) {
        StringBuilder resultado = new StringBuilder(valor.length() + 16);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                case '\b' -> resultado.append("\\b");
                case '\f' -> resultado.append("\\f");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> {
                    if (c < 0x20) {
                        resultado.append(String.format("\\u%04x", (int) c));
                    } else {
                        resultado.append(c);
                    }
                }
            }
        }
        return resultado.toString();
    }
}
