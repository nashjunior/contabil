package br.contabil.escrita;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import br.contabil.execucao.application.IngerirDotacoes;
import br.contabil.execucao.domain.CreditoAdicional;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.TipoCreditoAdicional;
import br.contabil.execucao.domain.repository.DotacaoRepository.ErroItemLote;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

@ExtendWith(MockitoExtension.class)
class DotacaoControllerTest {

    @Mock
    private IngerirDotacoes ingerirDotacoes;

    @Test
    void expoeIngerirDotacoesComoLoteHttpFailSoft() {
        UUID ente = UUID.randomUUID();
        UUID unidadeGestora = UUID.randomUUID();
        DotacaoId inserida = DotacaoId.novo();
        DotacaoId atualizada = DotacaoId.novo();
        Sessao sessao = sessao(ente);
        DotacaoController controller = new DotacaoController(ingerirDotacoes);
        DotacaoController.LoteDotacaoRequest request = new DotacaoController.LoteDotacaoRequest(
                List.of(new DotacaoController.FixacaoDotacaoRequest(
                        2026, "12.361.0021.2044", "0100000000", unidadeGestora, "150000.00")),
                List.of(new DotacaoController.CreditoAdicionalRequest(
                        atualizada.valor(), "suplementar", "10000.00", "Decreto 2026/0087")));

        when(ingerirDotacoes.executar(eq(sessao), eq(new TenantId(ente)), any(), any()))
                .thenReturn(new IngerirDotacoes.Resultado(
                        List.of(inserida),
                        List.of(atualizada),
                        List.of(new ErroItemLote(
                                "credito dotacaoId=" + atualizada.valor(),
                                "dotacao_nao_encontrada",
                                "dotação não encontrada para o ente — crédito não aplicado"))));

        ResponseEntity<DotacaoController.LoteDotacaoResponse> resposta = controller.ingerir(ente, request, sessao);

        assertThat(resposta.getStatusCode().value()).isEqualTo(207);
        assertThat(resposta.getBody().dotacoesInseridas()).containsExactly(inserida.valor());
        assertThat(resposta.getBody().dotacoesAtualizadas()).containsExactly(atualizada.valor());
        assertThat(resposta.getBody().erros())
                .containsExactly(new DotacaoController.ItemComErro(
                        "credito dotacaoId=" + atualizada.valor(),
                        "dotacao_nao_encontrada",
                        "dotação não encontrada para o ente — crédito não aplicado"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IngerirDotacoes.SolicitacaoFixacaoDotacao>> fixacoes =
                ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreditoAdicional>> creditos = ArgumentCaptor.forClass(List.class);
        verify(ingerirDotacoes)
                .executar(eq(sessao), eq(new TenantId(ente)), fixacoes.capture(), creditos.capture());
        assertThat(fixacoes.getValue().get(0).unidadeGestoraId().valor()).isEqualTo(unidadeGestora);
        assertThat(fixacoes.getValue().get(0).valorAutorizado().valor().toPlainString()).isEqualTo("150000.00");
        assertThat(creditos.getValue().get(0).tipo()).isEqualTo(TipoCreditoAdicional.SUPLEMENTAR);
    }

    @Test
    void itemNuloOuMalformadoViraErroDoLoteEmVezDeDerrubarComQuinhentos() {
        UUID ente = UUID.randomUUID();
        UUID unidadeGestora = UUID.randomUUID();
        DotacaoId inserida = DotacaoId.novo();
        Sessao sessao = sessao(ente);
        DotacaoController controller = new DotacaoController(ingerirDotacoes);

        // ArrayList (não List.of) porque o lote precisa aceitar o item nulo que o Jackson produz
        // para `{"fixacoes": [null]}` — é justamente o caso que antes virava NPE/500.
        List<DotacaoController.FixacaoDotacaoRequest> fixacoes = new ArrayList<>();
        fixacoes.add(null);
        fixacoes.add(new DotacaoController.FixacaoDotacaoRequest(
                2026, "12.361.0021.2044", "0100000000", unidadeGestora, "nao-e-numero"));
        fixacoes.add(new DotacaoController.FixacaoDotacaoRequest(
                2026, "12.361.0021.2044", "0100000000", unidadeGestora, "150000.00"));
        DotacaoController.LoteDotacaoRequest request = new DotacaoController.LoteDotacaoRequest(
                fixacoes,
                List.of(new DotacaoController.CreditoAdicionalRequest(
                        UUID.randomUUID(), "tipo-inexistente", "10000.00", "Decreto 2026/0087")));

        when(ingerirDotacoes.executar(eq(sessao), eq(new TenantId(ente)), any(), any()))
                .thenReturn(new IngerirDotacoes.Resultado(List.of(inserida), List.of(), List.of()));

        ResponseEntity<DotacaoController.LoteDotacaoResponse> resposta = controller.ingerir(ente, request, sessao);

        assertThat(resposta.getStatusCode().value()).isEqualTo(207);
        assertThat(resposta.getBody().dotacoesInseridas()).containsExactly(inserida.valor());
        assertThat(resposta.getBody().erros())
                .extracting(DotacaoController.ItemComErro::referencia)
                .containsExactly("fixacao[0]", "fixacao[1]", "credito[0]");

        // Só o item bom chega ao caso de uso — os rejeitados não abortam os demais (ADR-0013).
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IngerirDotacoes.SolicitacaoFixacaoDotacao>> capturadas =
                ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreditoAdicional>> creditos = ArgumentCaptor.forClass(List.class);
        verify(ingerirDotacoes).executar(eq(sessao), eq(new TenantId(ente)), capturadas.capture(), creditos.capture());
        assertThat(capturadas.getValue()).hasSize(1);
        assertThat(capturadas.getValue().get(0).valorAutorizado().valor().toPlainString()).isEqualTo("150000.00");
        assertThat(creditos.getValue()).isEmpty();
    }

    @Test
    void listaAusenteNoCorpoNaoQuebraOLote() {
        UUID ente = UUID.randomUUID();
        Sessao sessao = sessao(ente);
        DotacaoController controller = new DotacaoController(ingerirDotacoes);

        when(ingerirDotacoes.executar(eq(sessao), eq(new TenantId(ente)), any(), any()))
                .thenReturn(new IngerirDotacoes.Resultado(List.of(), List.of(), List.of()));

        ResponseEntity<DotacaoController.LoteDotacaoResponse> resposta =
                controller.ingerir(ente, new DotacaoController.LoteDotacaoRequest(null, null), sessao);

        assertThat(resposta.getStatusCode().value()).isEqualTo(207);
        assertThat(resposta.getBody().erros()).isEmpty();
    }

    private static Sessao sessao(UUID ente) {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                new TenantId(ente),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }
}
