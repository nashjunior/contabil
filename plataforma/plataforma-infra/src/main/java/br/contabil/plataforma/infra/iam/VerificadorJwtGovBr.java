package br.contabil.plataforma.infra.iam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.plataforma.domain.iam.ServicoIdentidade;

final class VerificadorJwtGovBr {

    private static final TypeReference<Map<String, Object>> MAPA_JSON = new TypeReference<>() {};
    private static final long SKEW_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final IamProperties.GovBr properties;
    private final PublicKey publicKey;

    VerificadorJwtGovBr(ObjectMapper objectMapper, Clock clock, IamProperties.GovBr properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.publicKey = chavePublica(properties.publicKeyPem());
    }

    IdentidadeVerificada verificar(String jwt) {
        try {
            String[] partes = Objects.requireNonNull(jwt, "jwt").split("\\.");
            if (partes.length != 3) {
                throw new ServicoIdentidade.NaoAutenticadoException("JWT gov.br malformado");
            }
            Map<String, Object> header = json(partes[0]);
            if (!"RS256".equals(header.get("alg"))) {
                throw new ServicoIdentidade.NaoAutenticadoException("JWT gov.br deve usar RS256");
            }
            verificarAssinatura(partes);
            Map<String, Object> claims = json(partes[1]);
            exigirIgual(claims.get("iss"), properties.issuer().toString(), "iss");
            exigirAudience(claims.get("aud"));
            Instant expiraEm = Instant.ofEpochSecond(numero(claims.get("exp"), "exp"));
            if (expiraEm.plusSeconds(SKEW_SECONDS).isBefore(Instant.now(clock))) {
                throw new ServicoIdentidade.NaoAutenticadoException("JWT gov.br expirado");
            }
            String cpf = IamProperties.normalizarCpf(texto(claims.get(properties.cpfClaim()), properties.cpfClaim()));
            UUID enteId = UUID.fromString(texto(claims.get(properties.enteClaim()), properties.enteClaim()));
            String orgao = claims.containsKey(properties.orgaoClaim())
                    ? texto(claims.get(properties.orgaoClaim()), properties.orgaoClaim())
                    : null;
            boolean mfaForte = nivelForte(claims.get(properties.nivelClaim()));
            return new IdentidadeVerificada(cpf, enteId, orgao, mfaForte, expiraEm);
        } catch (ServicoIdentidade.NaoAutenticadoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServicoIdentidade.NaoAutenticadoException("claim gov.br invalido: " + e.getMessage());
        }
    }

    private void verificarAssinatura(String[] partes) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(partes[2]))) {
                throw new ServicoIdentidade.NaoAutenticadoException("assinatura do JWT gov.br invalida");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new ServicoIdentidade.NaoAutenticadoException("assinatura do JWT gov.br invalida");
        }
    }

    private Map<String, Object> json(String parteBase64Url) {
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(parteBase64Url), MAPA_JSON);
        } catch (IOException e) {
            throw new ServicoIdentidade.NaoAutenticadoException("JWT gov.br contem JSON invalido");
        }
    }

    private void exigirIgual(Object valor, String esperado, String claim) {
        if (!esperado.equals(valor)) {
            throw new ServicoIdentidade.NaoAutenticadoException("claim gov.br " + claim + " invalido");
        }
    }

    private void exigirAudience(Object aud) {
        if (aud instanceof String texto && properties.audience().equals(texto)) {
            return;
        }
        if (aud instanceof List<?> lista && lista.contains(properties.audience())) {
            return;
        }
        throw new ServicoIdentidade.NaoAutenticadoException("claim gov.br aud invalido");
    }

    private static long numero(Object valor, String claim) {
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        throw new ServicoIdentidade.NaoAutenticadoException("claim gov.br " + claim + " ausente");
    }

    private static String texto(Object valor, String claim) {
        if (valor instanceof String texto && !texto.isBlank()) {
            return texto.trim();
        }
        throw new ServicoIdentidade.NaoAutenticadoException("claim gov.br " + claim + " ausente");
    }

    private static boolean nivelForte(Object valor) {
        if (!(valor instanceof String texto)) {
            return false;
        }
        String normalizado = texto.trim().toUpperCase(java.util.Locale.ROOT);
        return normalizado.equals("PRATA") || normalizado.equals("OURO");
    }

    private static PublicKey chavePublica(String pem) {
        try {
            String normalizado = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalizado);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("siafic.iam.govbr.public-key-pem invalida", e);
        }
    }
}
