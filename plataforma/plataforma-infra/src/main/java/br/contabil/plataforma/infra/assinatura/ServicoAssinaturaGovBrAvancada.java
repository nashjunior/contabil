package br.contabil.plataforma.infra.assinatura;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link ServicoAssinatura} F0: provedor único gov.br avançada
 * (transversais/01-assinatura-eletronica.md §Faseamento). Orquestra: elegibilidade
 * (delegada ao 403 do próprio provedor — ver {@link ProvedorAssinaturaGovBr}), preparo
 * do placeholder PAdES + hash SHA-256 sobre o byte-range (ver
 * {@link PreparadorAssinaturaPades}), assinatura, checagem de revogação (item 6),
 * incorporação do CMS/PKCS#7 no PDF (item 3), manifesto (item 8) e trilha (item 9).
 *
 * <p><b>Fora do F0 desta classe</b> (ver package-info): fluxo interativo OAuth2
 * de autorização do signatário; leitura/gravação real do documento no object
 * store (ADR-0009) — recebidos como colaboradores injetados; workflow
 * multi-assinatura e validação completa via ITI (F1).
 */
public final class ServicoAssinaturaGovBrAvancada implements ServicoAssinatura {

    private final ProvedorAssinaturaGovBr provedor;
    private final VerificadorRevogacaoCertificado verificadorRevogacao;
    private final AuditoriaEscrita trilha;
    private final Function<ReferenciaDocumento, byte[]> leitorDocumento;
    private final BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado;
    private final Function<byte[], X509Certificate> extratorCertificado;
    private final PreparadorAssinaturaPades preparadorPades = new PreparadorAssinaturaPades();
    private final Clock clock;

    public ServicoAssinaturaGovBrAvancada(
            ProvedorAssinaturaGovBr provedor,
            VerificadorRevogacaoCertificado verificadorRevogacao,
            AuditoriaEscrita trilha,
            Function<ReferenciaDocumento, byte[]> leitorDocumento,
            BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado,
            Clock clock) {
        this(
                provedor,
                verificadorRevogacao,
                trilha,
                leitorDocumento,
                publicadorDocumentoAssinado,
                new ExtratorCertificadoPkcs7(),
                clock);
    }

    ServicoAssinaturaGovBrAvancada(
            ProvedorAssinaturaGovBr provedor,
            VerificadorRevogacaoCertificado verificadorRevogacao,
            AuditoriaEscrita trilha,
            Function<ReferenciaDocumento, byte[]> leitorDocumento,
            BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado,
            Function<byte[], X509Certificate> extratorCertificado,
            Clock clock) {
        this.provedor = Objects.requireNonNull(provedor, "provedor");
        this.verificadorRevogacao = Objects.requireNonNull(verificadorRevogacao, "verificadorRevogacao");
        this.trilha = Objects.requireNonNull(trilha, "trilha");
        this.leitorDocumento = Objects.requireNonNull(leitorDocumento, "leitorDocumento");
        this.publicadorDocumentoAssinado =
                Objects.requireNonNull(publicadorDocumentoAssinado, "publicadorDocumentoAssinado");
        this.extratorCertificado = Objects.requireNonNull(extratorCertificado, "extratorCertificado");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DocumentoAssinado assinar(DocumentoParaAssinar documento, NivelAssinatura nivel, List<Signatario> signatarios) {
        Objects.requireNonNull(documento, "documento");
        Objects.requireNonNull(nivel, "nivel");
        if (signatarios == null || signatarios.isEmpty()) {
            throw new IllegalArgumentException("ao menos um signatário é exigido");
        }
        if (nivel != NivelAssinatura.AVANCADA_GOVBR) {
            throw new NivelInsuficienteException(
                    "provedor F0 só assina no nível AVANCADA_GOVBR; ICP-Brasil qualificada é F1");
        }
        if (signatarios.size() > 1) {
            throw new UnsupportedOperationException(
                    "workflow de múltiplas assinaturas por papel é F1 (transversais/01 item 7)");
        }
        Signatario signatario = signatarios.get(0);

        byte[] conteudo = leitorDocumento.apply(documento.origem());
        UUID idTransacao = UUID.randomUUID();
        Instant momento = clock.instant();

        // Fase 1 (ISO 32000 §12.8): reserva o placeholder de assinatura (/Sig, /ByteRange)
        // no PDF e calcula o hash sobre esse byte-range JÁ preparado. É esse hash — não o
        // do documento bruto — que precisa virar o input de assinarPkcs7: se o provedor
        // assinasse o hash do PDF sem placeholder, o /ByteRange do dicionário de assinatura
        // não bateria com o que foi realmente assinado (PAdES inválido).
        PreparadorAssinaturaPades.PreparoAssinaturaPades preparo =
                preparadorPades.preparar(conteudo, signatario.nome(), momento);
        byte[] hash = preparo.hashSha256();
        String hashBase64 = Base64.getEncoder().encodeToString(hash);

        // O bloco abaixo é o que pode falhar antes da fase 2 (provedor, extração do
        // certificado, checagem de revogação) — QUALQUER RuntimeException daqui precisa
        // descartar o placeholder (fecha o PDDocument aberto por preparadorPades). Não dá
        // para confiar só nas exceções nomeadas: um provedor real (ex.: falha de rede/HTTP
        // inesperado em ProvedorAssinaturaGovBrHttp) lança IllegalStateException, não
        // ContaGovBrNaoElegivelException, e vazaria o handle se só a nomeada fosse capturada.
        byte[] pkcs7;
        try {
            try {
                pkcs7 = provedor.assinarPkcs7(hash);
            } catch (ProvedorAssinaturaGovBr.ContaGovBrNaoElegivelException e) {
                registrarNaTrilha(documento, signatario, nivel, hashBase64, idTransacao, momento, true, "conta não elegível: " + e.getMessage());
                throw new CertificadoInvalidoException(e.getMessage());
            }

            X509Certificate certificado;
            try {
                certificado = extratorCertificado.apply(pkcs7);
            } catch (ExtratorCertificadoPkcs7.CertificadoPkcs7InvalidoException e) {
                String detalhe = "PKCS#7 devolvido pelo gov.br sem certificado legível: " + e.getMessage();
                registrarNaTrilha(documento, signatario, nivel, hashBase64, idTransacao, momento, true, detalhe);
                throw new CertificadoInvalidoException(detalhe);
            }

            VerificadorRevogacaoCertificado.ResultadoRevogacao resultadoRevogacao = verificadorRevogacao.verificar(certificado);
            registrarNaTrilha(
                    documento, signatario, nivel, hashBase64, idTransacao, momento,
                    resultadoRevogacao.revogado(), resultadoRevogacao.detalhe());

            if (resultadoRevogacao.revogado()) {
                throw new CertificadoInvalidoException(
                        "certificado do signatário reprovado na checagem de revogação: " + resultadoRevogacao.detalhe());
            }
        } catch (RuntimeException e) {
            preparo.descartar();
            throw e;
        }

        // Fase 2: só agora, com o PKCS#7 assinado sobre o hash correto, embute o CMS no
        // placeholder reservado (incremental save) — o PDF final PAdES só existe aqui.
        byte[] pdfAssinado = preparo.embutir(pkcs7);
        ReferenciaDocumento referenciaPublicada = publicadorDocumentoAssinado.apply(pdfAssinado, documento.origem());
        String manifesto = construirManifesto(documento, signatario, nivel, hashBase64, idTransacao, momento);

        return new DocumentoAssinado(referenciaPublicada, manifesto, hashBase64, idTransacao);
    }

    @Override
    public ResultadoVerificacao verificar(ReferenciaDocumento documento) {
        Objects.requireNonNull(documento, "documento");
        byte[] conteudo;
        try {
            conteudo = leitorDocumento.apply(documento);
        } catch (RuntimeException e) {
            return new ResultadoVerificacao(false, "documento não pôde ser lido: " + e.getMessage());
        }
        if (conteudo == null || conteudo.length == 0) {
            return new ResultadoVerificacao(false, "documento vazio ou inexistente na referência informada");
        }
        // F0: checagem estrutural mínima (documento legível e não vazio). A
        // validação completa — integridade + cadeia + revogação via Validador
        // do ITI (validar.iti.gov.br) — é F1 (transversais/01 item 10).
        return new ResultadoVerificacao(
                true, "checagem estrutural mínima OK (F0); validação completa via ITI é F1, não implementada");
    }

    private void registrarNaTrilha(
            DocumentoParaAssinar documento,
            Signatario signatario,
            NivelAssinatura nivel,
            String hashBase64,
            UUID idTransacao,
            Instant momento,
            boolean bloqueado,
            String detalhe) {
        trilha.append(new EventoAuditoria(
                documento.ente(),
                "assinatura_eletronica",
                signatario.cpf(),
                documento.origem().uri().toString(),
                momento,
                Map.of(
                        "idTransacao", idTransacao.toString(),
                        "tipoDocumento", documento.tipoDocumento(),
                        "nivel", nivel.name(),
                        "hashSha256Base64", hashBase64,
                        "bloqueado", String.valueOf(bloqueado),
                        "detalhe", detalhe)));
    }

    private static String construirManifesto(
            DocumentoParaAssinar documento,
            Signatario signatario,
            NivelAssinatura nivel,
            String hashBase64,
            UUID idTransacao,
            Instant momento) {
        return "signatario=%s (CPF %s); tipo=%s; nivel=%s; momento=%s; hash_sha256=%s; id_transacao=%s"
                .formatted(signatario.nome(), signatario.cpf(), documento.tipoDocumento(), nivel, momento, hashBase64, idTransacao);
    }
}
