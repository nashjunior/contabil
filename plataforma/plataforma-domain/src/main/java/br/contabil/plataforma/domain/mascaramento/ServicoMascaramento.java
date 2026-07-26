package br.contabil.plataforma.domain.mascaramento;

import java.util.Objects;

import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;

/**
 * Contrato do serviço de <b>Mascaramento</b> de dados pessoais (doc 11 §Contratos;
 * ref transversais/04-lgpd; Lei 13.709/2018).
 *
 * <p>Biblioteca <b>única</b> de plataforma (RAZ-12): PII é mascarada na fronteira
 * conforme a <b>audiência</b> e mediante <b>base legal</b> (LGPD art. 7º/11). Sem base
 * legal, {@link #mascarar} lança {@link SemBaseLegalException} — falha fechada, nunca
 * devolve o dado em claro. Todo acesso a dado pessoal gera o evento
 * {@code acesso_dado_pessoal} na trilha de auditoria (registrado via o port de Auditoria).
 */
public interface ServicoMascaramento {

    /**
     * Devolve o valor do {@code campo} apropriado à {@code audiencia}, dado o {@code contexto}.
     *
     * @throws SemBaseLegalException não há base legal para expor/tratar o dado nesse contexto
     */
    String mascarar(CampoSensivel campo, ContextoAcesso contexto, Audiencia audiencia);

    // ---- Value objects do contrato --------------------------------------------

    /** Categoria de dado pessoal — orienta a estratégia de mascaramento. */
    enum Categoria {
        CPF,
        NOME,
        ENDERECO,
        CONTATO,
        DADO_BANCARIO,
        DADO_SENSIVEL,
        OUTRO
    }

    /** Campo sensível: nome, valor em claro e a categoria. */
    record CampoSensivel(String nome, String valor, Categoria categoria) {
        public CampoSensivel {
            Objects.requireNonNull(nome, "nome do campo");
            Objects.requireNonNull(valor, "valor do campo");
            Objects.requireNonNull(categoria, "categoria do campo");
        }
    }

    /** Audiência do consumo — o portal público mascara mais que o back-office. */
    enum Audiencia {
        PORTAL_PUBLICO,
        BACKOFFICE,
        CONTROLE_EXTERNO,
        TITULAR
    }

    /**
     * Base legal do tratamento (LGPD art. 7º/11) — subconjunto pertinente ao setor
     * público. Não inclui {@code CONSENTIMENTO}: no setor público a base é sempre
     * obrigação legal/política pública/interesse público (transversais/04-lgpd
     * §Base legal; guardiao-seguranca regra 7) — consentimento como base padrão é
     * erro jurídico, não uma opção de configuração.
     */
    enum BaseLegalLgpd {
        OBRIGACAO_LEGAL,
        EXECUCAO_POLITICAS_PUBLICAS,
        INTERESSE_PUBLICO
    }

    /** Contexto do acesso: ente, quem solicita, finalidade e base legal invocada. */
    record ContextoAcesso(TenantId ente, String solicitante, String finalidade, BaseLegalLgpd baseLegal) {
        public ContextoAcesso {
            Objects.requireNonNull(ente, "ente do contexto");
            Objects.requireNonNull(solicitante, "solicitante do acesso");
            Objects.requireNonNull(finalidade, "finalidade do acesso");
            Objects.requireNonNull(baseLegal, "base legal do acesso");
        }
    }

    // ---- Erros do contrato (doc 11 §Contratos) --------------------------------

    /** Erro {@code sem_base_legal}: não há base legal para tratar/expor o dado no contexto. */
    final class SemBaseLegalException extends RuntimeException implements ErroContrato {
        public SemBaseLegalException(String mensagem) {
            super(mensagem);
        }

        @Override
        public String codigo() {
            return "sem_base_legal";
        }
    }
}
