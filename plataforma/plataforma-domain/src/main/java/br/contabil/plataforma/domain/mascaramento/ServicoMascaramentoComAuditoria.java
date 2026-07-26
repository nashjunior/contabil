package br.contabil.plataforma.domain.mascaramento;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;

/**
 * Decorador que fecha a cobertura mínima de trilha do piso F0 para PII (13-nfr §piso;
 * guardiao-seguranca item 6; RAZ-37): toda chamada a {@link ServicoMascaramento#mascarar}
 * gera o evento {@code acesso_dado_pessoal} na trilha de auditoria — sucesso OU negado por
 * falta de base legal — ANTES de devolver ao chamador.
 *
 * <p>Não decide política de mascaramento (isso é do {@code delegado}, entregue à parte —
 * RAZ-12); só garante que nenhum acesso a dado pessoal escapa sem registro. Se
 * {@link AuditoriaEscrita#append} falhar, a {@link AuditoriaEscrita.FalhaAppendException}
 * sobe e a leitura de PII aborta — nunca segue sem trilha (contrato de {@code append}).
 *
 * <p>{@code detalhes} nunca carrega o valor em claro do campo (só nome/categoria) — o
 * próprio evento de auditoria não pode virar um vazamento de PII (javadoc de
 * {@link EventoAuditoria}).
 */
public final class ServicoMascaramentoComAuditoria implements ServicoMascaramento {

    private static final String EVENTO_ACESSO_DADO_PESSOAL = "acesso_dado_pessoal";

    private final ServicoMascaramento delegado;
    private final AuditoriaEscrita trilha;
    private final Clock relogio;

    public ServicoMascaramentoComAuditoria(ServicoMascaramento delegado, AuditoriaEscrita trilha, Clock relogio) {
        this.delegado = Objects.requireNonNull(delegado, "delegado de mascaramento");
        this.trilha = Objects.requireNonNull(trilha, "trilha de auditoria");
        this.relogio = Objects.requireNonNull(relogio, "relógio");
    }

    @Override
    public String mascarar(CampoSensivel campo, ContextoAcesso contexto, Audiencia audiencia) {
        Objects.requireNonNull(campo, "campo sensível");
        Objects.requireNonNull(contexto, "contexto de acesso");
        Objects.requireNonNull(audiencia, "audiência");

        try {
            String resultado = delegado.mascarar(campo, contexto, audiencia);
            registrar(campo, contexto, audiencia, "permitido");
            return resultado;
        } catch (SemBaseLegalException negado) {
            registrar(campo, contexto, audiencia, "negado");
            throw negado;
        }
    }

    private void registrar(CampoSensivel campo, ContextoAcesso contexto, Audiencia audiencia, String resultado) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("campo", campo.nome());
        detalhes.put("categoria", campo.categoria().name());
        detalhes.put("audiencia", audiencia.name());
        detalhes.put("finalidade", contexto.finalidade());
        detalhes.put("base_legal", contexto.baseLegal().name());
        detalhes.put("resultado", resultado);

        trilha.append(new EventoAuditoria(
                contexto.ente(),
                EVENTO_ACESSO_DADO_PESSOAL,
                contexto.solicitante(),
                campo.nome(),
                Instant.now(relogio),
                detalhes));
    }
}
