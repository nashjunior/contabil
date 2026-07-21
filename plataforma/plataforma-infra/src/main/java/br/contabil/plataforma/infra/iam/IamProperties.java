package br.contabil.plataforma.infra.iam;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "siafic.iam")
record IamProperties(
        boolean enabled,
        GovBr govbr,
        Icp icp,
        Duration sessionTtl,
        List<Concessao> concessoes) {

    private static final Duration SESSION_TTL_PADRAO = Duration.ofHours(8);

    IamProperties {
        govbr = govbr == null ? GovBr.vazio() : govbr;
        icp = icp == null ? Icp.vazio() : icp;
        sessionTtl = sessionTtl == null ? SESSION_TTL_PADRAO : sessionTtl;
        concessoes = concessoes == null ? List.of() : List.copyOf(concessoes);
        if (!sessionTtl.isPositive()) {
            throw new IllegalArgumentException("siafic.iam.session-ttl deve ser positivo");
        }
    }

    void validar() {
        if (!enabled) {
            return;
        }
        govbr.validar();
        if (concessoes.isEmpty()) {
            throw new IllegalStateException("siafic.iam.concessoes deve declarar a matriz RBAC");
        }
        concessoes.forEach(Concessao::validar);
    }

    record GovBr(
            URI issuer,
            String audience,
            String publicKeyPem,
            String cpfClaim,
            String enteClaim,
            String orgaoClaim,
            String nivelClaim) {

        static GovBr vazio() {
            return new GovBr(null, "", "", "cpf", "ente_id", "orgao", "nivel_conta");
        }

        GovBr {
            audience = audience == null ? "" : audience.trim();
            publicKeyPem = publicKeyPem == null ? "" : publicKeyPem.trim();
            cpfClaim = normalizarNomeClaim(cpfClaim, "cpf");
            enteClaim = normalizarNomeClaim(enteClaim, "ente_id");
            orgaoClaim = normalizarNomeClaim(orgaoClaim, "orgao");
            nivelClaim = normalizarNomeClaim(nivelClaim, "nivel_conta");
        }

        void validar() {
            if (issuer == null || !issuer.isAbsolute() || !"https".equals(issuer.getScheme())) {
                throw new IllegalStateException("siafic.iam.govbr.issuer deve ser URI https absoluta");
            }
            if (audience.isBlank()) {
                throw new IllegalStateException("siafic.iam.govbr.audience deve ser configurado");
            }
            if (publicKeyPem.isBlank()) {
                throw new IllegalStateException("siafic.iam.govbr.public-key-pem deve ser configurado");
            }
        }
    }

    record Icp(Set<String> certificadosConfiaveisSha256) {
        static Icp vazio() {
            return new Icp(Set.of());
        }

        Icp {
            certificadosConfiaveisSha256 = certificadosConfiaveisSha256 == null
                    ? Set.of()
                    : certificadosConfiaveisSha256.stream()
                            .map(valor -> valor.replace(":", "").trim().toLowerCase(Locale.ROOT))
                            .filter(valor -> !valor.isBlank())
                            .collect(Collectors.toUnmodifiableSet());
        }
    }

    record Concessao(
            String cpf,
            UUID enteId,
            String orgao,
            Set<Papel> papeis) {

        Concessao {
            cpf = normalizarCpf(cpf);
            Objects.requireNonNull(enteId, "enteId");
            orgao = orgao == null || orgao.isBlank() ? null : orgao.trim();
            papeis = papeis == null ? Set.of() : Set.copyOf(papeis);
        }

        Optional<String> orgaoOptional() {
            return Optional.ofNullable(orgao);
        }

        void validar() {
            if (papeis.isEmpty()) {
                throw new IllegalStateException("concessao de " + mascararCpf(cpf) + " sem papel RBAC");
            }
            if (papeis.contains(Papel.LANCADOR) && papeis.contains(Papel.AUTORIZADOR)) {
                throw conflito("LANCADOR", "AUTORIZADOR");
            }
            if (papeis.contains(Papel.AUTORIZADOR) && papeis.contains(Papel.PAGADOR)) {
                throw conflito("AUTORIZADOR", "PAGADOR");
            }
            if (papeis.contains(Papel.ADMIN_ACESSO) && !Collections.disjoint(papeis, Papel.OPERACIONAIS_FINANCEIROS)) {
                throw conflito("ADMIN_ACESSO", "papel operacional financeiro");
            }
        }

        private IllegalStateException conflito(String a, String b) {
            return new IllegalStateException(
                    "segregacao de funcoes violada para " + mascararCpf(cpf) + ": " + a + " conflita com " + b);
        }
    }

    enum Papel {
        LANCADOR,
        AUTORIZADOR,
        PAGADOR,
        ADMIN_ACESSO,
        ADMIN_PLATAFORMA,
        AUDITOR,
        PUBLICADOR;

        static final Set<Papel> OPERACIONAIS_FINANCEIROS = Set.of(LANCADOR, AUTORIZADOR, PAGADOR);

        boolean permite(ServicoIdentidade.Recurso recurso, ServicoIdentidade.Acao acao) {
            String urn = recurso.urn();
            return switch (this) {
                case LANCADOR -> urn.equals("razao:fato_contabil")
                        && (acao == ServicoIdentidade.Acao.CRIAR || acao == ServicoIdentidade.Acao.ESTORNAR);
                case AUTORIZADOR -> urn.equals("razao:fato_contabil")
                        && (acao == ServicoIdentidade.Acao.APROVAR || acao == ServicoIdentidade.Acao.ASSINAR);
                case PAGADOR -> urn.equals("execucao:pagamento")
                        && (acao == ServicoIdentidade.Acao.CRIAR
                                || acao == ServicoIdentidade.Acao.APROVAR
                                || acao == ServicoIdentidade.Acao.ASSINAR);
                case ADMIN_ACESSO -> urn.equals("plataforma:iam") && acao == ServicoIdentidade.Acao.ALTERAR;
                case ADMIN_PLATAFORMA -> urn.startsWith("plataforma:");
                case AUDITOR -> acao == ServicoIdentidade.Acao.LER;
                case PUBLICADOR -> urn.startsWith("transparencia:") && acao == ServicoIdentidade.Acao.PUBLICAR;
            };
        }
    }

    static String normalizarCpf(String cpf) {
        Objects.requireNonNull(cpf, "cpf");
        String digitos = cpf.replaceAll("\\D", "");
        if (!digitos.matches("\\d{11}")) {
            throw new IllegalArgumentException("CPF deve ter 11 digitos");
        }
        return digitos;
    }

    static String mascararCpf(String cpf) {
        String digitos = normalizarCpf(cpf);
        return "***." + digitos.substring(3, 6) + ".***-**";
    }

    private static String normalizarNomeClaim(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
