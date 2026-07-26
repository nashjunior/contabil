package br.contabil.plataforma.domain.cofre;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * Contrato do <b>Cofre de segredos</b> (doc 11 §Cofre/gestão de segredos).
 *
 * <p>Serviço <b>único</b> de plataforma: nada de segredo em código, repositório ou
 * config versionada. No F0, a implementação aceita é passthrough de ambiente/secret file
 * da esteira; Secrets Manager/Vault/KMS/HSM entra por outro adapter sem mudar o port.
 * Contas de serviço usam <b>privilégio mínimo</b> e escopo restrito por integração.
 */
public interface CofreSegredos {

    /**
     * Obtém o valor de um segredo para a conta de serviço, respeitando o escopo.
     *
     * @throws SemEscopoException      a conta não tem escopo para esse segredo (privilégio mínimo)
     * @throws SegredoExpiradoException o segredo/token está expirado
     */
    ValorSegredo obter(ContaServico conta, ReferenciaSegredo segredo);

    /** Rotaciona o segredo (nova versão), invalidando a anterior conforme política. */
    void rotacionar(ReferenciaSegredo segredo);

    // ---- Value objects do contrato --------------------------------------------

    /** Conta de serviço com escopos explícitos (privilégio mínimo por integração). */
    record ContaServico(String nome, Set<String> escopos) {
        public ContaServico {
            Objects.requireNonNull(nome, "nome da conta de serviço");
            escopos = Set.copyOf(Objects.requireNonNullElse(escopos, Set.of()));
        }
    }

    /** Referência estável a um segredo no cofre (caminho lógico, nunca o valor). */
    record ReferenciaSegredo(String caminho) {
        public ReferenciaSegredo {
            Objects.requireNonNull(caminho, "caminho do segredo");
            if (caminho.isBlank()) {
                throw new IllegalArgumentException("caminho do segredo não pode ser vazio");
            }
        }
    }

    /**
     * Valor de um segredo com validade. <b>Não é record</b> de propósito: o valor
     * <b>nunca</b> aparece em {@code toString}/log (segredo só no cofre — AGENTS.md,
     * guardiao-seguranca). Guardado como {@code char[]} para permitir limpeza da memória.
     */
    final class ValorSegredo {
        private final char[] valor;
        private final Instant expiraEm;

        public ValorSegredo(char[] valor, Instant expiraEm) {
            this.valor = Objects.requireNonNull(valor, "valor do segredo").clone();
            this.expiraEm = Objects.requireNonNull(expiraEm, "expiração do segredo");
        }

        /** Cópia defensiva do valor — o chamador deve limpá-la após o uso. */
        public char[] valor() {
            return valor.clone();
        }

        public Instant expiraEm() {
            return expiraEm;
        }

        /** Segredo redigido — jamais expor o valor em log/trilha. */
        @Override
        public String toString() {
            return "ValorSegredo[REDIGIDO, expiraEm=" + expiraEm + "]";
        }
    }

    // ---- Erros do contrato (doc 11 §Contratos) --------------------------------

    /** Erro {@code sem_escopo}: a conta de serviço não pode acessar esse segredo. */
    final class SemEscopoException extends RuntimeException implements ErroContrato {
        public SemEscopoException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "sem_escopo";
        }
    }

    /** Erro {@code expirado}: segredo/token expirado — renovar/rotacionar. */
    final class SegredoExpiradoException extends RuntimeException implements ErroContrato {
        public SegredoExpiradoException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "expirado";
        }
    }
}
