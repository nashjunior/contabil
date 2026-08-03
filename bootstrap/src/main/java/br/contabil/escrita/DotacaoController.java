package br.contabil.escrita;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

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
import br.contabil.plataforma.domain.ErroContrato;
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
        List<ItemComErro> errosConversao = new ArrayList<>();
        List<IngerirDotacoes.SolicitacaoFixacaoDotacao> fixacoes =
                converter(requisicao.fixacoes(), FixacaoDotacaoRequest::paraUseCase, "fixacao", errosConversao);
        List<CreditoAdicional> creditos =
                converter(requisicao.creditos(), CreditoAdicionalRequest::paraDominio, "credito", errosConversao);

        IngerirDotacoes.Resultado resultado = ingerirDotacoes.executar(sessao, new TenantId(enteId), fixacoes, creditos);

        return ResponseEntity.status(207).body(LoteDotacaoResponse.de(resultado, errosConversao));
    }

    /**
     * Converte DTO → domínio item a item, em fail-soft (ADR-0013): item nulo ou malformado
     * (valor não numérico, UUID/tipo de crédito inválido, campo obrigatório ausente) vira
     * {@code errors[]} do 207 em vez de derrubar o lote inteiro com 500 — mesma postura do
     * lote de pagamentos. A conversão é feita aqui, e não no caso de uso, porque
     * {@link IngerirDotacoes} recebe as listas já tipadas e persiste em batch
     * ({@code ON CONFLICT}, RAZ-89); um item ruim nunca chegaria ao try/catch por item de lá.
     *
     * <p>A referência é posicional ({@code fixacao[2]}) porque um item malformado pode não ter
     * campo algum utilizável para identificá-lo.
     */
    private static <T, R> List<R> converter(
            List<T> itens, Function<T, R> conversao, String referencia, List<ItemComErro> erros) {
        List<T> entrada = listaOuVazia(itens);
        List<R> convertidos = new ArrayList<>(entrada.size());
        for (int i = 0; i < entrada.size(); i++) {
            T item = entrada.get(i);
            if (item == null) {
                erros.add(new ItemComErro("%s[%d]".formatted(referencia, i), "item_invalido", "item nulo"));
                continue;
            }
            try {
                convertidos.add(conversao.apply(item));
            } catch (RuntimeException erro) {
                erros.add(new ItemComErro("%s[%d]".formatted(referencia, i), codigo(erro), erro.getMessage()));
            }
        }
        return convertidos;
    }

    private static String codigo(RuntimeException erro) {
        return erro instanceof ErroContrato erroContrato ? erroContrato.codigo() : "item_invalido";
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

        /**
         * {@code errosConversao} são os itens rejeitados na borda, antes do caso de uso — vêm
         * primeiro porque foram descartados mais cedo no pipeline. Os demais são os erros por
         * item devolvidos pelo lote.
         */
        static LoteDotacaoResponse de(IngerirDotacoes.Resultado resultado, List<ItemComErro> errosConversao) {
            List<ItemComErro> erros = new ArrayList<>(errosConversao);
            resultado.erros().stream().map(ItemComErro::de).forEach(erros::add);
            return new LoteDotacaoResponse(
                    resultado.dotacoesInseridas().stream().map(DotacaoId::valor).toList(),
                    resultado.dotacoesAtualizadas().stream().map(DotacaoId::valor).toList(),
                    List.copyOf(erros));
        }
    }

    record ItemComErro(String referencia, String codigo, String mensagem) {

        static ItemComErro de(ErroItemLote erro) {
            return new ItemComErro(erro.referencia(), erro.codigo(), erro.mensagem());
        }
    }
}
