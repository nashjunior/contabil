package br.contabil.plataforma.infra.cofre;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.cofre.CofreSegredos;
import br.contabil.plataforma.domain.cofre.CofreSegredos.ContaServico;
import br.contabil.plataforma.domain.cofre.CriptografiaDadosSensiveis.CategoriaDadoSensivel;
import br.contabil.plataforma.domain.cofre.CriptografiaDadosSensiveis.ReferenciaChave;

class AesGcmCriptografiaDadosSensiveisTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);
    private static final ReferenciaChave CHAVE = new ReferenciaChave("cofre://siafic/f0/criptografia/dados-sensiveis", 3);

    @Test
    void cifraEDecifraGuardandoReferenciaDeChaveSemTextoClaro() {
        var criptografia = new AesGcmCriptografiaDadosSensiveis(cofreComChave(), conta(), CLOCK, new SecureRandom());

        var cifrado = criptografia.cifrar(
                CategoriaDadoSensivel.DADO_BANCARIO,
                "001-12345-6".getBytes(StandardCharsets.UTF_8),
                CHAVE);

        assertThat(cifrado.chave()).isEqualTo(CHAVE);
        assertThat(cifrado.algoritmo()).isEqualTo("AES/GCM/NoPadding");
        assertThat(cifrado.toString()).doesNotContain("001-12345-6").contains("REDIGIDO");
        assertThat(new String(criptografia.decifrar(cifrado), StandardCharsets.UTF_8)).isEqualTo("001-12345-6");
    }

    @Test
    void aadImpedeDecifrarComOutraCategoriaOuVersaoDeChave() {
        var criptografia = new AesGcmCriptografiaDadosSensiveis(cofreComChave(), conta(), CLOCK, new SecureRandom());
        var cifrado = criptografia.cifrar(
                CategoriaDadoSensivel.CREDENCIAL_INTEGRACAO,
                "client-secret".getBytes(StandardCharsets.UTF_8),
                CHAVE);
        var adulterado = new br.contabil.plataforma.domain.cofre.CriptografiaDadosSensiveis.DadoCifrado(
                CategoriaDadoSensivel.DADO_BANCARIO,
                cifrado.ciphertext(),
                cifrado.nonce(),
                cifrado.algoritmo(),
                cifrado.chave(),
                cifrado.cifradoEm());

        assertThatThrownBy(() -> criptografia.decifrar(adulterado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha ao decifrar");
    }

    private static CofreSegredos cofreComChave() {
        String chaveBase64 = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
        return new CofreSegredos() {
            @Override
            public ValorSegredo obter(ContaServico conta, ReferenciaSegredo segredo) {
                return new ValorSegredo(chaveBase64.toCharArray(), Instant.parse("2026-07-19T12:15:00Z"));
            }

            @Override
            public void rotacionar(ReferenciaSegredo segredo) {}
        };
    }

    private static ContaServico conta() {
        return new ContaServico("crypto", Set.of("cofre://siafic/f0/criptografia"));
    }
}
