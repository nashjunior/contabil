package br.contabil.plataforma.domain.iam;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;

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
        static final Pattern CPF_CLARO = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");

        public Cpf {
            Objects.requireNonNull(numero, "CPF não pode ser nulo");
            if (numero.isBlank()) {
                throw new IllegalArgumentException("CPF não pode ser vazio");
            }
        }

        /**
         * Forma mascarada ({@code "***.456.***-**"}, mesmo padrão de exibição do contrato de
         * API — RAZ-79 §5.3) para uso onde o CPF em claro não pode aparecer, ex.: {@code ator}
         * de um evento de auditoria (a trilha tem guardrail de banco,
         * {@code ck_auditoria_evento_sem_cpf_claro}, que rejeita qualquer sequência com formato
         * de CPF em {@code ator}/{@code recurso}/{@code detalhes} — RAZ-105).
         */
        public String mascarado() {
            String digitos = numero.replaceAll("[^0-9]", "");
            if (digitos.length() != 11) {
                return "*".repeat(numero.length());
            }
            return "***." + digitos.substring(3, 6) + ".***-**";
        }

        /**
         * Remove CPF cru de textos de fronteira/trilha preservando contexto operacional.
         * Útil para mensagens de erro de provedores externos antes de anexar na auditoria.
         */
        public static String mascararOcorrencias(String texto) {
            Objects.requireNonNull(texto, "texto");
            Matcher matcher = CPF_CLARO.matcher(texto);
            StringBuilder mascarado = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(mascarado, Matcher.quoteReplacement(new Cpf(matcher.group()).mascarado()));
            }
            matcher.appendTail(mascarado);
            return mascarado.toString();
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
