package br.contabil.razao.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.FonteRecurso;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ContadorFatoPort;
import br.contabil.razao.domain.repository.DisponibilidadePorFontePort;
import br.contabil.razao.domain.repository.DisponibilidadePorFontePort.SaldoPorFonte;
import br.contabil.razao.domain.repository.FatoContabilRepository;

@ExtendWith(MockitoExtension.class)
class EncerrarDdrPorFonteTest {

    @Mock
    private FatoContabilRepository repositorio;

    @Mock
    private ContadorFatoPort contadorFato;

    @Mock
    private DisponibilidadePorFontePort disponibilidadePorFonte;

    @Mock
    private AuditoriaEscrita auditoria;

    private EncerrarDdrPorFonte encerramentoDdr;

    private final TenantId enteId = TenantId.de(UUID.randomUUID().toString());
    private final PeriodoContabilId periodoId = PeriodoContabilId.novo();
    private final LocalDate dataEncerramento = LocalDate.of(2026, 12, 31);
    private final Clock relogio = Clock.fixed(Instant.parse("2026-12-31T23:00:00Z"), ZoneOffset.UTC);

    private final ContaContabilId contaDdrUtilizada = ContaContabilId.novo();
    private final ContaContabilId contaControleDisponibilidade = ContaContabilId.novo();
    private final FonteRecurso fonteA = new FonteRecurso("100");

    private final ParametroEncerramentoDdr parametroDdr = new ParametroEncerramentoDdr(
            contaDdrUtilizada, contaControleDisponibilidade, Natureza.CREDITO);

    @BeforeEach
    void setUp() {
        encerramentoDdr =
                new EncerrarDdrPorFonte(repositorio, contadorFato, disponibilidadePorFonte, auditoria, relogio);
    }

    private Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), enteId, Optional.empty(), true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("gera fato de encerramento por fonte com naturezas corretas (D:origem utilizada, C:destino controle)")
    void geraFatoEncerramentoPorFonte() {
        Sessao sessao = sessao();
        when(disponibilidadePorFonte.consultarSaldoPorFonte(enteId, List.of(contaDdrUtilizada)))
                .thenReturn(List.of(new SaldoPorFonte(fonteA, Dinheiro.de("1000.00"))));
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L);

        List<FatoContabil> fatos = encerramentoDdr.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, List.of(parametroDdr));

        assertThat(fatos).hasSize(1);
        FatoContabil fato = fatos.get(0);
        assertThat(fato.tipoEvento()).isEqualTo(TipoEvento.ENCERRAMENTO_DDR);
        assertThat(fato.enteId()).isEqualTo(enteId);
        assertThat(fato.periodoId()).isEqualTo(periodoId);
        assertThat(fato.dataCompetencia()).isEqualTo(dataEncerramento);
        assertThat(fato.lancamentos()).hasSize(2);

        var lancamentos = fato.lancamentos();
        var lancamentoOrigem = lancamentos.stream()
                .filter(l -> l.contaId().equals(contaDdrUtilizada)).findFirst().orElseThrow();
        var lancamentoDestino = lancamentos.stream()
                .filter(l -> l.contaId().equals(contaControleDisponibilidade)).findFirst().orElseThrow();

        assertThat(lancamentoOrigem.natureza()).isEqualTo(Natureza.DEBITO);
        assertThat(lancamentoDestino.natureza()).isEqualTo(Natureza.CREDITO);
        assertThat(lancamentoOrigem.valor()).isEqualTo(Dinheiro.de("1000.00"));
        assertThat(lancamentoDestino.valor()).isEqualTo(Dinheiro.de("1000.00"));

        assertThat(lancamentoOrigem.fonteRecurso()).contains(fonteA);
        assertThat(lancamentoDestino.fonteRecurso()).contains(fonteA);

        verify(repositorio).inserir(fato);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).append(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo("razao_encerramento_ddr_por_fonte");
        assertThat(evento.getValue().detalhes())
                .containsEntry("exercicio", "2026")
                .containsEntry("fonte", "100")
                .containsEntry("valor", "1000.00");
    }

    @Test
    @DisplayName("fontes com saldo zero são ignoradas e não geram fatos")
    void ignoraFontesComSaldoZero() {
        Sessao sessao = sessao();
        when(disponibilidadePorFonte.consultarSaldoPorFonte(enteId, List.of(contaDdrUtilizada)))
                .thenReturn(List.of(new SaldoPorFonte(fonteA, Dinheiro.zero())));

        List<FatoContabil> fatos = encerramentoDdr.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, List.of(parametroDdr));

        assertThat(fatos).isEmpty();
        verify(repositorio, never()).inserir(any());
        verify(auditoria, never()).append(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("duas fontes com saldo geram fatos separados por fonte")
    void geraFatosPorFonte() {
        Sessao sessao = sessao();
        FonteRecurso fonteB = new FonteRecurso("200");
        when(disponibilidadePorFonte.consultarSaldoPorFonte(enteId, List.of(contaDdrUtilizada)))
                .thenReturn(List.of(
                        new SaldoPorFonte(fonteA, Dinheiro.de("500.00")),
                        new SaldoPorFonte(fonteB, Dinheiro.de("300.00"))));
        when(contadorFato.proximoNumeroSeq(enteId)).thenReturn(1L, 2L);

        List<FatoContabil> fatos = encerramentoDdr.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, List.of(parametroDdr));

        assertThat(fatos).hasSize(2);
        assertThat(fatos).allMatch(f -> f.tipoEvento() == TipoEvento.ENCERRAMENTO_DDR);
    }

    @Test
    @DisplayName("lista vazia de parâmetros não gera fatos nem consulta o port")
    void semParametrosNaoGeraFatos() {
        Sessao sessao = sessao();

        List<FatoContabil> fatos = encerramentoDdr.executar(
                sessao, enteId, 2026, periodoId, dataEncerramento, List.of());

        assertThat(fatos).isEmpty();
        verify(disponibilidadePorFonte, never()).consultarSaldoPorFonte(any(), any());
        verify(repositorio, never()).inserir(any());
    }
}
