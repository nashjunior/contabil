package br.contabil.plataforma.domain.cofre;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Contrato de cifragem em repouso para dados sensiveis fora do razão: credenciais
 * de integracao, dados bancarios e identificadores pessoais segregados.
 */
public interface CriptografiaDadosSensiveis {

    DadoCifrado cifrar(CategoriaDadoSensivel categoria, byte[] claro, ReferenciaChave chave);

    byte[] decifrar(DadoCifrado cifrado);

    enum CategoriaDadoSensivel {
        CREDENCIAL_INTEGRACAO,
        DADO_BANCARIO,
        PII_SEGREGADA
    }

    /** Referencia logica da chave KMS/HSM/cofre. Nunca contem material criptografico. */
    record ReferenciaChave(String caminho, int versao) {
        public ReferenciaChave {
            Objects.requireNonNull(caminho, "caminho da chave");
            if (caminho.isBlank()) {
                throw new IllegalArgumentException("caminho da chave nao pode ser vazio");
            }
            if (versao <= 0) {
                throw new IllegalArgumentException("versao da chave deve ser positiva");
            }
        }
    }

    /**
     * Envelope persistivel. O banco guarda ciphertext + metadados de chave/algoritmo,
     * nunca o dado claro nem a chave.
     */
    final class DadoCifrado {
        private final CategoriaDadoSensivel categoria;
        private final byte[] ciphertext;
        private final byte[] nonce;
        private final String algoritmo;
        private final ReferenciaChave chave;
        private final Instant cifradoEm;

        public DadoCifrado(
                CategoriaDadoSensivel categoria,
                byte[] ciphertext,
                byte[] nonce,
                String algoritmo,
                ReferenciaChave chave,
                Instant cifradoEm) {
            this.categoria = Objects.requireNonNull(categoria, "categoria");
            this.ciphertext = exigirBytes(ciphertext, "ciphertext");
            this.nonce = exigirBytes(nonce, "nonce");
            this.algoritmo = exigirTexto(algoritmo, "algoritmo");
            this.chave = Objects.requireNonNull(chave, "chave");
            this.cifradoEm = Objects.requireNonNull(cifradoEm, "cifradoEm");
        }

        public CategoriaDadoSensivel categoria() {
            return categoria;
        }

        public byte[] ciphertext() {
            return ciphertext.clone();
        }

        public byte[] nonce() {
            return nonce.clone();
        }

        public String algoritmo() {
            return algoritmo;
        }

        public ReferenciaChave chave() {
            return chave;
        }

        public Instant cifradoEm() {
            return cifradoEm;
        }

        @Override
        public String toString() {
            return "DadoCifrado[categoria=" + categoria
                    + ", ciphertext=REDIGIDO, algoritmo=" + algoritmo
                    + ", chave=" + chave + ", cifradoEm=" + cifradoEm + "]";
        }

        private static byte[] exigirBytes(byte[] valor, String campo) {
            Objects.requireNonNull(valor, campo);
            if (valor.length == 0) {
                throw new IllegalArgumentException(campo + " nao pode ser vazio");
            }
            return Arrays.copyOf(valor, valor.length);
        }

        private static String exigirTexto(String valor, String campo) {
            Objects.requireNonNull(valor, campo);
            if (valor.isBlank()) {
                throw new IllegalArgumentException(campo + " nao pode ser vazio");
            }
            return valor;
        }
    }
}
