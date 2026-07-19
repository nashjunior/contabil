package br.contabil.plataforma.domain.iam;

import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato do serviço de <b>Identidade/RBAC</b> (doc 11 §Contratos; refs
 * transversais/04-lgpd, transversais/05-regras).
 *
 * <p>Autenticação por <b>claim verificado</b> (gov.br SSO / certificado ICP-Brasil),
 * nunca usuário genérico. Autorização <b>deny-by-default</b>: {@link #autorizar} só
 * retorna {@code true} diante de concessão explícita, sempre com escopo por
 * <b>ente/órgão</b>. Este é o <i>port</i> estável consumido pelos módulos; a
 * implementação (adapter gov.br/ICP) é entregue à parte (RAZ-5).
 *
 * <p>Efeitos (eventos {@code login}/{@code negado}) são registrados via o port de
 * Auditoria, não retornados aqui.
 */
public interface ServicoIdentidade {

    /**
     * Autentica uma credencial verificada e abre uma sessão.
     *
     * @throws NaoAutenticadoException credencial inválida/não verificada
     * @throws MfaRequeridoException   segundo fator necessário (ver {@link #completarMfa})
     */
    Sessao autenticar(Credencial credencial);

    /**
     * Decide se a sessão pode executar {@code acao} sobre {@code recurso}.
     * Deny-by-default: {@code false} salvo concessão explícita no escopo da sessão.
     */
    boolean autorizar(Sessao sessao, Recurso recurso, Acao acao);

    /**
     * Completa o desafio de MFA e devolve a sessão elevada.
     *
     * @throws NaoAutenticadoException resposta de MFA inválida
     */
    Sessao completarMfa(DesafioMfa desafio, RespostaMfa resposta);

    // ---- Value objects do contrato --------------------------------------------

    /** Credencial verificada apresentada na autenticação (métodos plugáveis). */
    sealed interface Credencial permits CredencialGovBr, CredencialCertificadoIcp {}

    /** Asserção/token do gov.br SSO (nível avançado). */
    record CredencialGovBr(String assercao) implements Credencial {
        public CredencialGovBr {
            Objects.requireNonNull(assercao, "asserção gov.br não pode ser nula");
        }
    }

    /** Cadeia de certificado ICP-Brasil (PEM) — autenticação por certificado. */
    record CredencialCertificadoIcp(String cadeiaCertificadoPem) implements Credencial {
        public CredencialCertificadoIcp {
            Objects.requireNonNull(cadeiaCertificadoPem, "cadeia de certificado não pode ser nula");
        }
    }

    /** CPF do titular — identidade sempre nominal (sem usuário genérico). */
    record Cpf(String numero) {
        public Cpf {
            Objects.requireNonNull(numero, "CPF não pode ser nulo");
            if (numero.isBlank()) {
                throw new IllegalArgumentException("CPF não pode ser vazio");
            }
        }
    }

    /** Sessão autenticada com escopo por ente/órgão (isolamento multi-tenant). */
    record Sessao(
            UUID id,
            Cpf titular,
            TenantId ente,
            Optional<String> orgao,
            boolean mfaConcluido,
            Instant expiraEm) {
        public Sessao {
            Objects.requireNonNull(id, "id da sessão");
            Objects.requireNonNull(titular, "titular da sessão");
            Objects.requireNonNull(ente, "ente da sessão");
            Objects.requireNonNull(orgao, "orgao (Optional, nunca null)");
            Objects.requireNonNull(expiraEm, "expiração da sessão");
        }
    }

    /** Recurso protegido, identificado por URN estável (ex.: {@code razao:fato_contabil}). */
    record Recurso(String urn) {
        public Recurso {
            Objects.requireNonNull(urn, "urn do recurso");
            if (urn.isBlank()) {
                throw new IllegalArgumentException("urn do recurso não pode ser vazio");
            }
        }
    }

    /** Verbo de autorização — base da segregação de funções (Regras 7/9). */
    enum Acao {
        LER,
        CRIAR,
        ALTERAR,
        ESTORNAR,
        APROVAR,
        ASSINAR,
        PUBLICAR
    }

    /** Desafio de segundo fator emitido durante a autenticação. */
    record DesafioMfa(UUID sessaoId, String canal) {
        public DesafioMfa {
            Objects.requireNonNull(sessaoId, "sessaoId do desafio");
            Objects.requireNonNull(canal, "canal do desafio");
        }
    }

    /** Resposta do titular ao desafio de MFA. */
    record RespostaMfa(String codigo) {
        public RespostaMfa {
            Objects.requireNonNull(codigo, "código do MFA");
        }
    }

    // ---- Erros do contrato (doc 11 §Contratos) --------------------------------

    /** Erro {@code nao_autenticado}: credencial ausente/inválida/não verificada. */
    final class NaoAutenticadoException extends RuntimeException implements ErroContrato {
        public NaoAutenticadoException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "nao_autenticado";
        }
    }

    /** Erro {@code sem_permissao}: ação negada pelo RBAC (deny-by-default). */
    final class SemPermissaoException extends RuntimeException implements ErroContrato {
        public SemPermissaoException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "sem_permissao";
        }
    }

    /** Erro {@code mfa_requerido}: segundo fator necessário antes de prosseguir. */
    final class MfaRequeridoException extends RuntimeException implements ErroContrato {
        private final transient DesafioMfa desafio;

        public MfaRequeridoException(DesafioMfa desafio) {
            super("MFA requerido");
            this.desafio = desafio;
        }

        public DesafioMfa desafio() {
            return desafio;
        }

        @Override
        public String codigo() {
            return "mfa_requerido";
        }
    }
}
