package br.contabil.escrita;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.contabil.execucao.application.IngerirDotacoes;
import br.contabil.execucao.domain.CreditoAdicional;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.TipoCreditoAdicional;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.DotacaoRepository.ErroItemLote;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Borda HTTP da ingestão de dotações (RAZ-147): expõe {@link IngerirDotacoes}
 * 1:1 em lote fail-soft (ADR-0013/ADR-0038), sem endpoint singular.
 *
 * <p>RBAC CRIAR/ALTERAR permanece no caso de uso. A borda não toca no razão e
 * não abre lote transacional guarda-chuva; a persistência batch decide os
 * processados e rejeitados por item.
 */
@RestController
@RequestMapping("/api/v1/entes/{enteId}/execucao/dotacoes:lote")
final class DotacaoController {

    private final IngerirDotacoes ingerirDotacoes;

    DotacaoController(IngerirDotacoes ingerirDotacoes) {
        this.ingerirDotacoes = Objects.requireNonNull(ingerirDotacoes, "ingerirDotacoes");
    }

    @PostMapping
    ResponseEntity<LoteDotacaoResponse> ingerir(
            @PathVariable("enteId") UUID enteId, @RequestBody LoteDotacaoRequest requisicao, Sessao sessao) {
        IngerirDotacoes.Resultado resultado = ingerirDotacoes.executar(
                sessao,
                new TenantId(enteId),
                listaOuVazia(requisicao.fixacoes()).stream().map(FixacaoDotacaoRequest::paraUseCase).toList(),
                listaOuVazia(requisicao.creditos()).stream().map(CreditoAdicionalRequest::paraDominio).toList());

        return ResponseEntity.status(207).body(LoteDotacaoResponse.de(resultado));
    }

    private static <T> List<T> listaOuVazia(List<T> itens) {
        return itens == null ? List.of() : itens;
    }

    record LoteDotacaoRequest(List<FixacaoDotacaoRequest> fixacoes, List<CreditoAdicionalRequest> creditos) {}

    record FixacaoDotacaoRequest(
            int exercicio,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            UUID unidadeGestoraId,
            String valorAutorizado) {

        IngerirDotacoes.SolicitacaoFixacaoDotacao paraUseCase() {
            return new IngerirDotacoes.SolicitacaoFixacaoDotacao(
                    exercicio,
                    classificacaoOrcamentaria,
                    fonteRecurso,
                    new UnidadeGestoraId(unidadeGestoraId),
                    Dinheiro.de(valorAutorizado));
        }
    }

    record CreditoAdicionalRequest(UUID dotacaoId, String tipo, String valor, String historico) {

        CreditoAdicional paraDominio() {
            return new CreditoAdicional(
                    new DotacaoId(dotacaoId), TipoCreditoAdicional.deCodigo(tipo), Dinheiro.de(valor), historico);
        }
    }

    record LoteDotacaoResponse(List<UUID> dotacoesInseridas, List<UUID> dotacoesAtualizadas, List<ItemComErro> erros) {

        static LoteDotacaoResponse de(IngerirDotacoes.Resultado resultado) {
            return new LoteDotacaoResponse(
                    resultado.dotacoesInseridas().stream().map(DotacaoId::valor).toList(),
                    resultado.dotacoesAtualizadas().stream().map(DotacaoId::valor).toList(),
                    resultado.erros().stream().map(ItemComErro::de).toList());
        }
    }

    record ItemComErro(String referencia, String codigo, String mensagem) {

        static ItemComErro de(ErroItemLote erro) {
            return new ItemComErro(erro.referencia(), erro.codigo(), erro.mensagem());
        }
    }
}
