package br.contabil.plataforma.domain.mascaramento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.Audiencia;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.BaseLegalLgpd;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.CampoSensivel;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.Categoria;
import br.contabil.plataforma.domain.mascaramento.ServicoMascaramento.ContextoAcesso;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova a cobertura mínima do F0 para leitura/exportação de PII (13-nfr §piso;
 * guardiao-seguranca item 6; RAZ-37 AC2): todo acesso via {@link ServicoMascaramento}
 * gera evento {@code acesso_dado_pessoal} rastreável, e o evento nunca carrega o
 * valor em claro do dado pessoal.
 */
class ServicoMascaramentoComAuditoriaTest {

    private static final TenantId ENTE = new TenantId(UUID.randomUUID());
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-07-19T13:00:00Z"), ZoneOffset.UTC);

    private final TrilhaEmMemoria trilha = new TrilhaEmMemoria();

    @Test
    @DisplayName("acesso permitido a CPF gera evento acesso_dado_pessoal sem o valor em claro")
    void acessoPermitidoGeraEventoAuditavel() {
        ServicoMascaramento servico =
                new ServicoMascaramentoComAuditoria(new ServicoMascaramentoPadrao(), trilha, RELOGIO);
        // CPF sintético sem checksum válido (13-nfr §piso "sem PII real em não-produção").
        CampoSensivel cpf = new CampoSensivel("cpf", "11122233344", Categoria.CPF);
        ContextoAcesso contexto = new ContextoAcesso(ENTE, "auditor-1", "prestacao_de_contas", BaseLegalLgpd.OBRIGACAO_LEGAL);

        String resultado = servico.mascarar(cpf, contexto, Audiencia.PORTAL_PUBLICO);

        assertThat(resultado).isEqualTo("***.222.***-**");
        assertThat(trilha.eventos).hasSize(1);
        EventoAuditoria evento = trilha.eventos.get(0);
        assertThat(evento.tipo()).isEqualTo("acesso_dado_pessoal");
        assertThat(evento.ente()).isEqualTo(ENTE);
        assertThat(evento.ator()).isEqualTo("auditor-1");
        assertThat(evento.recurso()).isEqualTo("cpf");
        assertThat(evento.detalhes())
                .containsEntry("categoria", "CPF")
                .containsEntry("audiencia", "PORTAL_PUBLICO")
                .containsEntry("base_legal", "OBRIGACAO_LEGAL")
                .containsEntry("resultado", "permitido");
        // O evento não pode virar, ele mesmo, um vazamento de PII.
        assertThat(evento.detalhes().values()).noneMatch(valor -> valor.contains("11122233344"));
    }

    @Test
    @DisplayName("acesso negado por falta de base legal também gera evento auditável, e a exceção sobe")
    void acessoNegadoTambemGeraEventoAuditavel() {
        ServicoMascaramento delegadoQueNega = (campo, contexto, audiencia) -> {
            throw new ServicoMascaramento.SemBaseLegalException("sem base legal para " + campo.categoria());
        };
        ServicoMascaramento servico = new ServicoMascaramentoComAuditoria(delegadoQueNega, trilha, RELOGIO);
        // Endereço sintético, sem CEP real (13-nfr §piso "sem PII real em não-produção").
        CampoSensivel enderecoField = new CampoSensivel("endereco", "Rua Fixture, 123", Categoria.ENDERECO);
        // RAZ-33: setor público nunca usa CONSENTIMENTO como base legal (removido do enum) —
        // INTERESSE_PUBLICO é a base coerente com portal público/consulta pública.
        ContextoAcesso contexto =
                new ContextoAcesso(ENTE, "portal-publico", "consulta_publica", BaseLegalLgpd.INTERESSE_PUBLICO);

        assertThatThrownBy(() -> servico.mascarar(enderecoField, contexto, Audiencia.PORTAL_PUBLICO))
                .isInstanceOf(ServicoMascaramento.SemBaseLegalException.class);

        assertThat(trilha.eventos).hasSize(1);
        assertThat(trilha.eventos.get(0).detalhes()).containsEntry("resultado", "negado");
    }

    @Test
    @DisplayName("falha ao anexar na trilha aborta o acesso — nunca segue sem registro")
    void falhaNaTrilhaAbortaOAcesso() {
        AuditoriaEscrita trilhaQuebrada = evento -> {
            throw new AuditoriaEscrita.FalhaAppendException("store indisponível", null);
        };
        ServicoMascaramento servico =
                new ServicoMascaramentoComAuditoria(delegadoQueDevolve("valor"), trilhaQuebrada, RELOGIO);
        CampoSensivel campo = new CampoSensivel("cpf", "11122233344", Categoria.CPF);
        ContextoAcesso contexto = new ContextoAcesso(ENTE, "auditor-1", "fiscalizacao", BaseLegalLgpd.OBRIGACAO_LEGAL);

        assertThatThrownBy(() -> servico.mascarar(campo, contexto, Audiencia.CONTROLE_EXTERNO))
                .isInstanceOf(AuditoriaEscrita.FalhaAppendException.class);
    }

    private static ServicoMascaramento delegadoQueDevolve(String valor) {
        return (campo, contexto, audiencia) -> valor;
    }

    /** Fake de trilha em memória — só para provar o encadeamento da chamada, sem hash-chain real. */
    private static final class TrilhaEmMemoria implements AuditoriaEscrita {
        private final List<EventoAuditoria> eventos = new ArrayList<>();

        @Override
        public EntradaTrilha append(EventoAuditoria evento) {
            eventos.add(evento);
            return new EntradaTrilha(UUID.randomUUID(), "hash-" + eventos.size(), null, eventos.size());
        }
    }
}
