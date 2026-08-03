package br.contabil.plataforma.infra.assinatura;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

/**
 * {@link ServicoAssinatura}: provedor gov.br avançada (F0) e qualificada ICP-Brasil
 * via escopo OAuth {@code icp_brasil} (F1; transversais/01-assinatura-eletronica.md
 * §Faseamento). Orquestra: elegibilidade
 * (delegada ao 403 do próprio provedor — ver {@link ProvedorAssinaturaGovBr}), preparo
 * do placeholder PAdES + hash SHA-256 sobre o byte-range (ver
 * {@link PreparadorAssinaturaPades}), assinatura, checagem de revogação (item 6),
 * incorporação do CMS/PKCS#7 no PDF (item 3), manifesto (item 8) e trilha (item 9).
 *
 * <p><b>Fora do F0 desta classe</b> (ver package-info): fluxo interativo OAuth2
 * de autorização do signatário; leitura/gravação real do documento no object
 * store (ADR-0009) — recebidos como colaboradores injetados; chamada interativa ao
 * VALIDAR/ITI para relatório de conformidade.
 */
public final class ServicoAssinaturaGovBrAvancada implements ServicoAssinatura {

    private final ProvedorAssinaturaGovBr provedor;
    private final VerificadorRevogacaoCertificado verificadorRevogacao;
    private final AuditoriaEscrita trilha;
    private final Function<ReferenciaDocumento, byte[]> leitorDocumento;
    private final BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado;
    private final Consumer<DocumentoParaAssinar> validadorTenant;
    private final Function<byte[], X509Certificate> extratorCertificado;
    private final Predicate<NivelAssinatura> nivelSuportado;
    private final PreparadorAssinaturaPades preparadorPades = new PreparadorAssinaturaPades();
    private final Clock clock;

    public ServicoAssinaturaGovBrAvancada(
            ProvedorAssinaturaGovBr provedor,
            VerificadorRevogacaoCertificado verificadorRevogacao,
            AuditoriaEscrita trilha,
            Function<ReferenciaDocumento, byte[]> leitorDocumento,
            BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado,
            Consumer<DocumentoParaAssinar> validadorTenant,
            Clock clock) {
        this(
                provedor,
                verificadorRevogacao,
                trilha,
                leitorDocumento,
                publicadorDocumentoAssinado,
                validadorTenant,
                new ExtratorCertificadoPkcs7(),
                nivel -> nivel == NivelAssinatura.AVANCADA_GOVBR,
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
        this(provedor, verificadorRevogacao, trilha, leitorDocumento, publicadorDocumentoAssinado,
                doc -> {}, extratorCertificado, nivel -> true, clock);
    }

    public ServicoAssinaturaGovBrAvancada(
            ProvedorAssinaturaGovBr provedor,
            VerificadorRevogacaoCertificado verificadorRevogacao,
            AuditoriaEscrita trilha,
            Function<ReferenciaDocumento, byte[]> leitorDocumento,
            BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado,
            Consumer<DocumentoParaAssinar> validadorTenant,
            Predicate<NivelAssinatura> nivelSuportado,
            Clock clock) {
        this(
                provedor,
                verificadorRevogacao,
                trilha,
                leitorDocumento,
                publicadorDocumentoAssinado,
                validadorTenant,
                new ExtratorCertificadoPkcs7(),
                nivelSuportado,
                clock);
    }

    private ServicoAssinaturaGovBrAvancada(
            ProvedorAssinaturaGovBr provedor,
            VerificadorRevogacaoCertificado verificadorRevogacao,
            AuditoriaEscrita trilha,
            Function<ReferenciaDocumento, byte[]> leitorDocumento,
            BiFunction<byte[], ReferenciaDocumento, ReferenciaDocumento> publicadorDocumentoAssinado,
            Consumer<DocumentoParaAssinar> validadorTenant,
            Function<byte[], X509Certificate> extratorCertificado,
            Predicate<NivelAssinatura> nivelSuportado,
            Clock clock) {
        this.provedor = Objects.requireNonNull(provedor, "provedor");
        this.verificadorRevogacao = Objects.requireNonNull(verificadorRevogacao, "verificadorRevogacao");
        this.trilha = Objects.requireNonNull(trilha, "trilha");
        this.leitorDocumento = Objects.requireNonNull(leitorDocumento, "leitorDocumento");
        this.publicadorDocumentoAssinado =
                Objects.requireNonNull(publicadorDocumentoAssinado, "publicadorDocumentoAssinado");
        this.validadorTenant = Objects.requireNonNull(validadorTenant, "validadorTenant");
        this.extratorCertificado = Objects.requireNonNull(extratorCertificado, "extratorCertificado");
        this.nivelSuportado = Objects.requireNonNull(nivelSuportado, "nivelSuportado");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DocumentoAssinado assinar(DocumentoParaAssinar documento, NivelAssinatura nivel, List<Signatario> signatarios) {
        Objects.requireNonNull(documento, "documento");
        Objects.requireNonNull(nivel, "nivel");
        if (signatarios == null || signatarios.isEmpty()) {
            throw new IllegalArgumentException("ao menos um signatário é exigido");
        }
        if (!nivelSuportado.test(nivel)) {
            throw new NivelInsuficienteException(
                    "sessão/provedor de assinatura não está configurado para o nível exigido: " + nivel);
        }

        validadorTenant.accept(documento);
        byte[] conteudoAtual = leitorDocumento.apply(documento.origem());
        UUID idTransacao = UUID.randomUUID();
        Instant momento = clock.instant();
        AssinaturaAplicada ultimaAssinatura = null;
        for (Signatario signatario : signatarios) {
            ultimaAssinatura = aplicarAssinatura(documento, signatario, nivel, conteudoAtual, idTransacao, momento);
            conteudoAtual = ultimaAssinatura.pdfAssinado();
        }

        ReferenciaDocumento referenciaPublicada = publicadorDocumentoAssinado.apply(conteudoAtual, documento.origem());
        String manifesto = construirManifesto(documento, nivel, signatarios, ultimaAssinatura.hashBase64(), idTransacao, momento);

        return new DocumentoAssinado(referenciaPublicada, manifesto, ultimaAssinatura.hashBase64(), idTransacao);
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
        // Checagem local mínima. O relatório de conformidade do VALIDAR/ITI é evidência
        // operacional externa: o serviço público aceita upload/QR Code, mas não há API
        // servidor-a-servidor estável no contrato deste adapter.
        return new ResultadoVerificacao(
                true, "checagem estrutural mínima OK; relatório de conformidade deve ser emitido no VALIDAR/ITI");
    }

    // Sequência falível da fase 1 — provedor → extração do certificado → revogação — que
    // produz o PKCS#7 consumido pela fase 2. Extraída de assinar() para não aninhar estes
    // try dentro do try que descarta o placeholder em qualquer RuntimeException (S1141).
    private byte[] assinarEValidarCertificado(ContextoAssinatura contexto, byte[] hash) {
        byte[] pkcs7;
        try {
            pkcs7 = provedor.assinarPkcs7(hash, contexto.nivel());
        } catch (ProvedorAssinaturaGovBr.ContaGovBrNaoElegivelException e) {
            registrarNaTrilha(contexto, true, "conta não elegível: " + e.getMessage());
            throw new CertificadoInvalidoException(e.getMessage());
        }

        X509Certificate certificado;
        try {
            certificado = extratorCertificado.apply(pkcs7);
        } catch (ExtratorCertificadoPkcs7.CertificadoPkcs7InvalidoException e) {
            String detalhe = "PKCS#7 devolvido pelo gov.br sem certificado legível: " + e.getMessage();
            registrarNaTrilha(contexto, true, detalhe);
            throw new CertificadoInvalidoException(detalhe);
        }

        VerificadorRevogacaoCertificado.ResultadoRevogacao resultadoRevogacao = verificadorRevogacao.verificar(certificado);
        registrarNaTrilha(contexto, resultadoRevogacao.revogado(), resultadoRevogacao.detalhe());

        if (resultadoRevogacao.revogado()) {
            throw new CertificadoInvalidoException(
                    "certificado do signatário reprovado na checagem de revogação: " + resultadoRevogacao.detalhe());
        }
        return pkcs7;
    }

    private void registrarNaTrilha(ContextoAssinatura contexto, boolean bloqueado, String detalhe) {
        trilha.append(new EventoAuditoria(
                contexto.documento().ente(),
                "assinatura_eletronica",
                cpfMascarado(contexto.signatario().cpf()),
                contexto.documento().origem().uri().toString(),
                contexto.momento(),
                Map.of(
                        "idTransacao", contexto.idTransacao().toString(),
                        "tipoDocumento", contexto.documento().tipoDocumento(),
                        "nivel", contexto.nivel().name(),
                        "hashSha256Base64", contexto.hashBase64(),
                        "bloqueado", String.valueOf(bloqueado),
                        "detalhe", Cpf.mascararOcorrencias(detalhe))));
    }

    private AssinaturaAplicada aplicarAssinatura(
            DocumentoParaAssinar documento,
            Signatario signatario,
            NivelAssinatura nivel,
            byte[] conteudo,
            UUID idTransacao,
            Instant momento) {
        // Fase 1 (ISO 32000 §12.8): reserva o placeholder de assinatura (/Sig, /ByteRange)
        // no PDF e calcula o hash sobre esse byte-range JÁ preparado. É esse hash — não o
        // do documento bruto — que precisa virar o input de assinarPkcs7: se o provedor
        // assinasse o hash do PDF sem placeholder, o /ByteRange do dicionário de assinatura
        // não bateria com o que foi realmente assinado (PAdES inválido).
        PreparadorAssinaturaPades.PreparoAssinaturaPades preparo =
                preparadorPades.preparar(conteudo, nomeExposto(signatario), momento);
        byte[] hash = preparo.hashSha256();
        String hashBase64 = Base64.getEncoder().encodeToString(hash);
        ContextoAssinatura contexto =
                new ContextoAssinatura(documento, signatario, nivel, hashBase64, idTransacao, momento);

        // O bloco abaixo é o que pode falhar antes da fase 2 (provedor, extração do
        // certificado, checagem de revogação) — QUALQUER RuntimeException daqui precisa
        // descartar o placeholder (fecha o PDDocument aberto por preparadorPades). Não dá
        // para confiar só nas exceções nomeadas: um provedor real (ex.: falha de rede/HTTP
        // inesperado em ProvedorAssinaturaGovBrHttp) lança IllegalStateException, não
        // ContaGovBrNaoElegivelException, e vazaria o handle se só a nomeada fosse capturada.
        byte[] pkcs7;
        try {
            pkcs7 = assinarEValidarCertificado(contexto, hash);
        } catch (RuntimeException e) {
            preparo.descartar();
            throw e;
        }

        // Fase 2: só agora, com o PKCS#7 assinado sobre o hash correto, embute o CMS no
        // placeholder reservado (incremental save) — o PDF final PAdES só existe aqui.
        return new AssinaturaAplicada(preparo.embutir(pkcs7), hashBase64);
    }

    private static String construirManifesto(
            DocumentoParaAssinar documento,
            NivelAssinatura nivel,
            List<Signatario> signatarios,
            String hashBase64,
            UUID idTransacao,
            Instant momento) {
        String nomes = signatarios.stream()
                .map(signatario -> "%s (CPF %s)".formatted(nomeExposto(signatario), cpfMascarado(signatario.cpf())))
                .toList()
                .toString();
        return "signatarios=%s; tipo=%s; nivel=%s; momento=%s; hash_sha256=%s; id_transacao=%s"
                .formatted(
                        nomes,
                        documento.tipoDocumento(),
                        nivel,
                        momento,
                        hashBase64,
                        idTransacao);
    }

    private static String nomeExposto(Signatario signatario) {
        return Cpf.mascararOcorrencias(signatario.nome());
    }

    private static String cpfMascarado(String cpf) {
        return new Cpf(cpf).mascarado();
    }

    /**
     * Dados comuns de uma operação de assinatura, agrupados para não propagar 6-8
     * parâmetros por assinar()/trilha/manifesto (limite de 7 parâmetros dos guardrails).
     */
    private record ContextoAssinatura(
            DocumentoParaAssinar documento,
            Signatario signatario,
            NivelAssinatura nivel,
            String hashBase64,
            UUID idTransacao,
            Instant momento) {}

    private record AssinaturaAplicada(byte[] pdfAssinado, String hashBase64) {}
}
