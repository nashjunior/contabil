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
    record DadoCifrado(
            CategoriaDadoSensivel categoria,
            byte[] ciphertext,
            byte[] nonce,
            String algoritmo,
            ReferenciaChave chave,
            Instant cifradoEm) {

        public DadoCifrado {
            Objects.requireNonNull(categoria, "categoria");
            ciphertext = exigirBytes(ciphertext, "ciphertext");
            nonce = exigirBytes(nonce, "nonce");
            Objects.requireNonNull(algoritmo, "algoritmo");
            if (algoritmo.isBlank()) {
                throw new IllegalArgumentException("algoritmo nao pode ser vazio");
            }
            Objects.requireNonNull(chave, "chave");
            Objects.requireNonNull(cifradoEm, "cifradoEm");
        }

        @Override
        public byte[] ciphertext() {
            return ciphertext.clone();
        }

        @Override
        public byte[] nonce() {
            return nonce.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DadoCifrado outro)) {
                return false;
            }
            return categoria == outro.categoria
                    && Arrays.equals(ciphertext, outro.ciphertext)
                    && Arrays.equals(nonce, outro.nonce)
                    && algoritmo.equals(outro.algoritmo)
                    && chave.equals(outro.chave)
                    && cifradoEm.equals(outro.cifradoEm);
        }

        @Override
        public int hashCode() {
            int resultado = Objects.hash(categoria, algoritmo, chave, cifradoEm);
            resultado = 31 * resultado + Arrays.hashCode(ciphertext);
            resultado = 31 * resultado + Arrays.hashCode(nonce);
            return resultado;
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
    }
}
