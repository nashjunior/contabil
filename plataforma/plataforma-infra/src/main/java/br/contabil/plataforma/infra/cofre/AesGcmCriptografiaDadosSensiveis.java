package br.contabil.plataforma.infra.cofre;

import br.contabil.plataforma.domain.cofre.CofreSegredos;
import br.contabil.plataforma.domain.cofre.CofreSegredos.ContaServico;
import br.contabil.plataforma.domain.cofre.CofreSegredos.ReferenciaSegredo;
import br.contabil.plataforma.domain.cofre.CriptografiaDadosSensiveis;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmCriptografiaDadosSensiveis implements CriptografiaDadosSensiveis {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_NONCE_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final CofreSegredos cofre;
    private final ContaServico contaServico;
    private final Clock clock;
    private final SecureRandom random;

    public AesGcmCriptografiaDadosSensiveis(CofreSegredos cofre, ContaServico contaServico, Clock clock) {
        this(cofre, contaServico, clock, new SecureRandom());
    }

    AesGcmCriptografiaDadosSensiveis(
            CofreSegredos cofre, ContaServico contaServico, Clock clock, SecureRandom random) {
        this.cofre = Objects.requireNonNull(cofre, "cofre");
        this.contaServico = Objects.requireNonNull(contaServico, "contaServico");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public DadoCifrado cifrar(CategoriaDadoSensivel categoria, byte[] claro, ReferenciaChave chave) {
        Objects.requireNonNull(categoria, "categoria");
        byte[] claroSeguro = exigirNaoVazio(claro, "claro");
        Objects.requireNonNull(chave, "chave");
        byte[] nonce = new byte[TAMANHO_NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] chaveBytes = materialChave(chave);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(chaveBytes, "AES"), new GCMParameterSpec(TAMANHO_TAG_BITS, nonce));
            cipher.updateAAD(aad(categoria, chave));
            return new DadoCifrado(categoria, cipher.doFinal(claroSeguro), nonce, ALGORITMO, chave, clock.instant());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("falha ao cifrar dado sensivel", e);
        } finally {
            Arrays.fill(chaveBytes, (byte) 0);
        }
    }

    @Override
    public byte[] decifrar(DadoCifrado cifrado) {
        Objects.requireNonNull(cifrado, "cifrado");
        if (!ALGORITMO.equals(cifrado.algoritmo())) {
            throw new IllegalArgumentException("algoritmo de cifragem nao suportado: " + cifrado.algoritmo());
        }
        byte[] chaveBytes = materialChave(cifrado.chave());
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(chaveBytes, "AES"),
                    new GCMParameterSpec(TAMANHO_TAG_BITS, cifrado.nonce()));
            cipher.updateAAD(aad(cifrado.categoria(), cifrado.chave()));
            return cipher.doFinal(cifrado.ciphertext());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("falha ao decifrar dado sensivel", e);
        } finally {
            Arrays.fill(chaveBytes, (byte) 0);
        }
    }

    private byte[] materialChave(ReferenciaChave chave) {
        char[] segredo = cofre.obter(contaServico, new ReferenciaSegredo(chave.caminho())).valor();
        try {
            byte[] material = Base64.getDecoder().decode(new String(segredo));
            if (material.length != 16 && material.length != 24 && material.length != 32) {
                throw new IllegalStateException("chave AES deve ter 128, 192 ou 256 bits");
            }
            return material;
        } finally {
            Arrays.fill(segredo, '\0');
        }
    }

    private static byte[] aad(CategoriaDadoSensivel categoria, ReferenciaChave chave) {
        byte[] caminho = chave.caminho().getBytes(StandardCharsets.UTF_8);
        byte[] categoriaBytes = categoria.name().getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(categoriaBytes.length + caminho.length + Integer.BYTES)
                .put(categoriaBytes)
                .put(caminho)
                .putInt(chave.versao())
                .array();
    }

    private static byte[] exigirNaoVazio(byte[] valor, String campo) {
        Objects.requireNonNull(valor, campo);
        if (valor.length == 0) {
            throw new IllegalArgumentException(campo + " nao pode ser vazio");
        }
        return valor.clone();
    }
}
