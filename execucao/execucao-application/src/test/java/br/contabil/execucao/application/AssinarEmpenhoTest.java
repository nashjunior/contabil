package br.contabil.execucao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoAssinaturaConflitanteException;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.NivelAssinaturaExigidoPort;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.CertificadoInvalidoException;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.DocumentoAssinado;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.ReferenciaDocumento;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.MfaRequeridoException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class AssinarEmpenhoTest {

    private static final Recurso RECURSO = new Recurso("execucao:empenho");

    @Mock
    private ServicoIdentidade servicoIdentidade;

    @Mock
    private EmpenhoRepository repositorio;

    @Mock
    private NivelAssinaturaExigidoPort nivelAssinaturaExigido;

    @Mock
    private ServicoAssinatura servicoAssinatura;

    @Mock
    private AuditoriaEscrita auditoria;

    private AssinarEmpenho useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final EmpenhoId empenhoId = EmpenhoId.novo();
    private final Cpf ordenador = new Cpf("33333333333");
    private final Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new AssinarEmpenho(
                new ControleAcesso(servicoIdentidade), repositorio, nivelAssinaturaExigido, servicoAssinatura,
                auditoria, relogioFixo);
    }

    private Sessao sessao(boolean mfaConcluido) {
        return new Sessao(UUID.randomUUID(), ordenador, enteId, Optional.empty(), mfaConcluido, Instant.parse("2030-01-01T00:00:00Z"));
    }

    private Empenho empenhoPendenteAssinatura(URI documentoPendenteUri) {
        return Empenho.registrar(
                        empenhoId,
                        enteId,
                        1L,
                        2026,
                        TipoEmpenho.ORDINARIO,
                        DotacaoId.novo(),
                        CredorId.novo(),
                        UnidadeGestoraId.novo(),
                        null,
                        Dinheiro.de("1000.00"),
                        LocalDate.of(2026, 7, 20),
                        "04.122.0001.2001",
                        "0100000000",
                        "empenho de material de expediente",
                        new ReferenciaFatoContabil(UUID.randomUUID()),
                        new Cpf("11122233344"))
                .marcarPendenteAssinatura(documentoPendenteUri);
    }

    private URI documentoPendenteUri() {
        return URI.create("s3://ged/%s/execucao/empenho/%s/nota-empenho.pdf".formatted(enteId.valor(), empenhoId.valor()));
    }

    @Test
    @DisplayName("assina com sucesso: resolve nível pelo port, chama ServicoAssinatura e persiste DOCUMENTO_ASSINADO")
    void assinaComSucesso() {
        Sessao sessao = sessao(true);
        Empenho pendente = empenhoPendenteAssinatura(documentoPendenteUri());
        DocumentoAssinado resultado = new DocumentoAssinado(
                new ReferenciaDocumento(URI.create("s3://ged/%s/x-assinado.pdf".formatted(enteId.valor()))),
                "manifesto",
                "hash-sha256",
                UUID.randomUUID());

        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ASSINAR)).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(pendente));
        when(nivelAssinaturaExigido.nivelExigido(enteId, "nota_empenho")).thenReturn(NivelAssinatura.AVANCADA_GOVBR);
        when(servicoAssinatura.assinar(any(), eq(NivelAssinatura.AVANCADA_GOVBR), any())).thenReturn(resultado);
        when(repositorio.marcarAssinado(eq(enteId), eq(empenhoId), any(DocumentoAssinadoEmpenho.class))).thenReturn(true);

        Empenho assinado = useCase.executar(sessao, enteId, empenhoId);

        assertThat(assinado.status().name()).isEqualTo("ASSINADO");
        ArgumentCaptor<DocumentoAssinadoEmpenho> documentoCaptor = ArgumentCaptor.forClass(DocumentoAssinadoEmpenho.class);
        verify(repositorio).marcarAssinado(eq(enteId), eq(empenhoId), documentoCaptor.capture());
        assertThat(documentoCaptor.getValue().hashSha256()).isEqualTo("hash-sha256");
        assertThat(documentoCaptor.getValue().signatario()).isEqualTo(ordenador);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_empenho_assinado");
    }

    @Test
    @DisplayName("RBAC/MFA: exige Acao.ASSINAR + MFA antes de tocar o repositório ou o serviço de assinatura")
    void exigeMfaAntesDeQualquerIo() {
        Sessao sessao = sessao(false);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ASSINAR)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, empenhoId))
                .isInstanceOf(MfaRequeridoException.class);

        verifyNoInteractions(repositorio, nivelAssinaturaExigido, servicoAssinatura, auditoria);
    }

    @Test
    @DisplayName("idempotência (R3): update condicional perdido vira conflito, nunca uma segunda assinatura")
    void duploCliqueViraConflito() {
        Sessao sessao = sessao(true);
        Empenho pendente = empenhoPendenteAssinatura(documentoPendenteUri());
        DocumentoAssinado resultado = new DocumentoAssinado(
                new ReferenciaDocumento(URI.create("s3://ged/%s/x-assinado.pdf".formatted(enteId.valor()))),
                "manifesto",
                "hash-sha256",
                UUID.randomUUID());

        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ASSINAR)).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(pendente));
        when(nivelAssinaturaExigido.nivelExigido(enteId, "nota_empenho")).thenReturn(NivelAssinatura.AVANCADA_GOVBR);
        when(servicoAssinatura.assinar(any(), eq(NivelAssinatura.AVANCADA_GOVBR), any())).thenReturn(resultado);
        when(repositorio.marcarAssinado(eq(enteId), eq(empenhoId), any(DocumentoAssinadoEmpenho.class))).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, empenhoId))
                .isInstanceOf(EmpenhoAssinaturaConflitanteException.class);

        verify(auditoria, never()).append(any());
    }

    @Test
    @DisplayName("empenho já REGISTRADO (documento ainda não gerado) falha antes de chamar o provedor de assinatura")
    void falhaQuandoAindaNaoPendenteDeAssinatura() {
        Sessao sessao = sessao(true);
        Empenho registrado = Empenho.registrar(
                empenhoId, enteId, 1L, 2026, TipoEmpenho.ORDINARIO, DotacaoId.novo(), CredorId.novo(),
                UnidadeGestoraId.novo(), null, Dinheiro.de("1000.00"), LocalDate.of(2026, 7, 20),
                "04.122.0001.2001", "0100000000", "empenho", new ReferenciaFatoContabil(UUID.randomUUID()), new Cpf("11122233344"));

        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ASSINAR)).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(registrado));

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, empenhoId))
                .isInstanceOf(EmpenhoAssinaturaConflitanteException.class);

        verifyNoInteractions(nivelAssinaturaExigido, servicoAssinatura, auditoria);
    }

    @Test
    @DisplayName("certificado inválido: marca ASSINATURA_REJEITADA (retrabalho, R2), grava trilha e propaga o erro")
    void certificadoInvalidoMarcaRejeitada() {
        Sessao sessao = sessao(true);
        Empenho pendente = empenhoPendenteAssinatura(documentoPendenteUri());

        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ASSINAR)).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(pendente));
        when(nivelAssinaturaExigido.nivelExigido(enteId, "nota_empenho")).thenReturn(NivelAssinatura.AVANCADA_GOVBR);
        when(servicoAssinatura.assinar(any(), eq(NivelAssinatura.AVANCADA_GOVBR), any()))
                .thenThrow(new CertificadoInvalidoException("certificado revogado"));
        when(repositorio.marcarAssinaturaRejeitada(enteId, empenhoId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, empenhoId))
                .isInstanceOf(CertificadoInvalidoException.class);

        verify(repositorio).marcarAssinaturaRejeitada(enteId, empenhoId);
        verify(repositorio, never()).marcarAssinado(any(), any(), any());
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_empenho_assinatura_rejeitada");
    }

    @Test
    @DisplayName("certificado inválido perdendo a corrida (empenho já ASSINADO por outra chamada) não grava trilha de rejeição")
    void certificadoInvalidoComTransicaoPerdidaNaoGravaTrilha() {
        Sessao sessao = sessao(true);
        Empenho pendente = empenhoPendenteAssinatura(documentoPendenteUri());

        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ASSINAR)).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(pendente));
        when(nivelAssinaturaExigido.nivelExigido(enteId, "nota_empenho")).thenReturn(NivelAssinatura.AVANCADA_GOVBR);
        when(servicoAssinatura.assinar(any(), eq(NivelAssinatura.AVANCADA_GOVBR), any()))
                .thenThrow(new CertificadoInvalidoException("certificado revogado"));
        when(repositorio.marcarAssinaturaRejeitada(enteId, empenhoId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, empenhoId))
                .isInstanceOf(CertificadoInvalidoException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("empenho inexistente falha alto em vez de silenciar")
    void empenhoInexistenteFalha() {
        Sessao sessao = sessao(true);
        when(servicoIdentidade.autorizar(sessao, RECURSO, Acao.ASSINAR)).thenReturn(true);
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(sessao, enteId, empenhoId))
                .isInstanceOf(ExecucaoInvalidaException.class);

        verifyNoInteractions(nivelAssinaturaExigido, servicoAssinatura, auditoria);
    }
}
