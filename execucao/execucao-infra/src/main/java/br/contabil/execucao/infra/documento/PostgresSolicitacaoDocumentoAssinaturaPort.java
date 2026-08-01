package br.contabil.execucao.infra.documento;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.execucao.application.SolicitacaoDocumentoAssinaturaPort;
import br.contabil.execucao.domain.Empenho;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.plataforma.infra.observabilidade.CorrelacaoIds;

/**
 * Enfileira {@code execucao_empenho_pendente_documento} na tabela dedicada
 * {@code execucao_empenho_documento_outbox} (ADR-0027 (a); mesma forma de
 * {@code PublicadorTransparenciaExecucao}, outbox transacional próprio — não o
 * {@code outbox_mensagem}/{@code ServicoEntrega} genérico de entrega externa,
 * já que o consumidor aqui ({@code EmpenhoDocumentoPendenteWorker}) processa o
 * evento localmente, não despacha a um broker externo).
 *
 * <p>Roda dentro da MESMA transação/conexão de {@code RegistrarEmpenho} (RLS já
 * ativa via {@code app.ente_id}, RAZ-21): um {@code insert} simples, sem
 * SECURITY DEFINER — o {@code unique (ente_id, empenho_id)} é rede de segurança
 * contra reenfileiramento acidental, não o mecanismo principal de idempotência
 * (o evento é publicado uma única vez por registro de empenho).
 */
@Repository
class PostgresSolicitacaoDocumentoAssinaturaPort implements SolicitacaoDocumentoAssinaturaPort {

    private static final String SQL_INSERT =
            """
            insert into execucao_empenho_documento_outbox (ente_id, empenho_id, correlation_id)
            values (?, ?, ?)
            on conflict (ente_id, empenho_id) do nothing
            """;

    private final JdbcTemplate jdbcTemplate;

    PostgresSolicitacaoDocumentoAssinaturaPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void solicitar(Empenho empenho, Sessao sessao) {
        jdbcTemplate.update(SQL_INSERT, empenho.enteId().valor(), empenho.id().valor(), CorrelacaoIds.atualOuNovo());
    }
}
