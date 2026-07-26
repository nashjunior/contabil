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
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.GeradorNotaEmpenhoPdf;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.EmpenhoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos.DocumentoJaExistenteException;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

@ExtendWith(MockitoExtension.class)
class GerarDocumentoEmpenhoTest {

    @Mock
    private EmpenhoRepository repositorio;

    @Mock
    private GeradorNotaEmpenhoPdf gerador;

    @Mock
    private ArmazenamentoDocumentos armazenamento;

    @Mock
    private AuditoriaEscrita auditoria;

    private GerarDocumentoEmpenho useCase;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final EmpenhoId empenhoId = EmpenhoId.novo();
    private final byte[] pdf = {1, 2, 3};

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
        useCase = new GerarDocumentoEmpenho(repositorio, gerador, armazenamento, "ged", auditoria, relogioFixo);
    }

    private Empenho empenhoRegistrado() {
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
                new Cpf("12345678901"));
    }

    private URI destinoEsperado() {
        return URI.create(
                "s3://ged/%s/execucao/empenho/%s/nota-empenho.pdf".formatted(enteId.valor(), empenhoId.valor()));
    }

    @Test
    @DisplayName("gera o PDF, publica no object store e transiciona para PENDENTE_ASSINATURA")
    void geraDocumentoComSucesso() {
        Empenho empenho = empenhoRegistrado();
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho));
        when(gerador.gerar(empenho)).thenReturn(pdf);
        when(armazenamento.armazenar(eq(pdf), any(URI.class))).thenReturn(destinoEsperado());
        when(repositorio.marcarPendenteAssinatura(enteId, empenhoId, destinoEsperado())).thenReturn(true);

        useCase.executar(enteId, empenhoId);

        verify(armazenamento).armazenar(pdf, destinoEsperado());
        verify(repositorio).marcarPendenteAssinatura(enteId, empenhoId, destinoEsperado());
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("execucao_empenho_documento_gerado");
    }

    @Test
    @DisplayName("idempotente: empenho que já saiu de REGISTRADO não gera PDF de novo")
    void reprocessamentoAposTransicaoENoOp() {
        Empenho jaPendente = empenhoRegistrado().marcarPendenteAssinatura(destinoEsperado());
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(jaPendente));

        useCase.executar(enteId, empenhoId);

        verifyNoInteractions(gerador, armazenamento, auditoria);
        verify(repositorio, never()).marcarPendenteAssinatura(any(), any(), any());
    }

    @Test
    @DisplayName("idempotente: retry pós-crash com o PDF já publicado trata DocumentoJaExistenteException como sucesso")
    void retryComDocumentoJaExistenteContinuaParaTransicao() {
        Empenho empenho = empenhoRegistrado();
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho));
        when(gerador.gerar(empenho)).thenReturn(pdf);
        when(armazenamento.armazenar(eq(pdf), any(URI.class)))
                .thenThrow(new DocumentoJaExistenteException("já existe"));
        when(repositorio.marcarPendenteAssinatura(enteId, empenhoId, destinoEsperado())).thenReturn(true);

        useCase.executar(enteId, empenhoId);

        verify(repositorio).marcarPendenteAssinatura(enteId, empenhoId, destinoEsperado());
    }

    @Test
    @DisplayName("corrida perdida contra outra entrega do mesmo evento não grava trilha")
    void transicaoConcorrentePerdidaNaoGravaTrilha() {
        Empenho empenho = empenhoRegistrado();
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.of(empenho));
        when(gerador.gerar(empenho)).thenReturn(pdf);
        when(armazenamento.armazenar(eq(pdf), any(URI.class))).thenReturn(destinoEsperado());
        when(repositorio.marcarPendenteAssinatura(enteId, empenhoId, destinoEsperado())).thenReturn(false);

        useCase.executar(enteId, empenhoId);

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("empenho inexistente falha alto em vez de silenciar")
    void empenhoInexistenteFalha() {
        when(repositorio.buscarPorId(enteId, empenhoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(enteId, empenhoId))
                .isInstanceOf(ExecucaoInvalidaException.class);

        verifyNoInteractions(gerador, armazenamento, auditoria);
    }
}
