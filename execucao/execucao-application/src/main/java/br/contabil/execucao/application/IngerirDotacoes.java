package br.contabil.execucao.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import br.contabil.execucao.domain.CreditoAdicional;
import br.contabil.execucao.domain.Dotacao;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.DotacaoRepository;
import br.contabil.execucao.domain.repository.DotacaoRepository.ErroItemLote;
import br.contabil.execucao.domain.repository.DotacaoRepository.ResultadoLote;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Acao;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Recurso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;

/**
 * Caso de uso: ingestão em lote da Dotação — Fixação (carga da LOA, B.2a) e créditos
 * adicionais (Lei 4.320/1964 arts. 40-46), fail-soft (ADR-0013).
 *
 * <p>Fluxo: RBAC/MFA (CRIAR para Fixação, ALTERAR para crédito adicional — segregação de
 * função, cada verbo só é cobrado se o lote tiver itens daquele tipo) -> valida cada item em
 * memória (um item ruim não impede os demais) -> persiste em lote por port (batch/{@code ON
 * CONFLICT}, RAZ-89) -> registra um evento de auditoria resumindo o lote. A transação é
 * responsabilidade da borda/infra.
 */
public class IngerirDotacoes {

    private static final Recurso RECURSO_DOTACAO = new Recurso("execucao:dotacao");

    private final ControleAcesso controleAcesso;
    private final DotacaoRepository repositorio;
    private final AuditoriaEscrita auditoria;
    private final Clock clock;

    public IngerirDotacoes(
            ControleAcesso controleAcesso, DotacaoRepository repositorio, AuditoriaEscrita auditoria, Clock clock) {
        this.controleAcesso = controleAcesso;
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public Resultado executar(
            Sessao usuarioAutenticado,
            TenantId enteId,
            List<SolicitacaoFixacaoDotacao> fixacoes,
            List<CreditoAdicional> creditos) {
        if (!fixacoes.isEmpty()) {
            controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_DOTACAO, Acao.CRIAR);
        }
        if (!creditos.isEmpty()) {
            controleAcesso.exigir(usuarioAutenticado, enteId, RECURSO_DOTACAO, Acao.ALTERAR);
        }
        if (fixacoes.isEmpty() && creditos.isEmpty()) {
            return new Resultado(List.of(), List.of(), List.of());
        }

        List<ErroItemLote> errosValidacao = new ArrayList<>();

        List<Dotacao> candidatasInsercao = new ArrayList<>();
        for (int i = 0; i < fixacoes.size(); i++) {
            SolicitacaoFixacaoDotacao item = fixacoes.get(i);
            try {
                candidatasInsercao.add(Dotacao.registrar(
                        DotacaoId.novo(),
                        enteId,
                        item.exercicio(),
                        item.classificacaoOrcamentaria(),
                        item.fonteRecurso(),
                        item.unidadeGestoraId(),
                        item.valorAutorizado()));
            } catch (RuntimeException e) {
                errosValidacao.add(new ErroItemLote(
                        "fixacao[%d] classificacao=%s fonte=%s"
                                .formatted(i, item.classificacaoOrcamentaria(), item.fonteRecurso()),
                        e.getMessage()));
            }
        }

        List<CreditoAdicional> candidatosCredito = new ArrayList<>();
        for (CreditoAdicional credito : creditos) {
            try {
                Dotacao.validarValorAutorizado(credito.valor());
                candidatosCredito.add(credito);
            } catch (RuntimeException e) {
                errosValidacao.add(new ErroItemLote(
                        "credito dotacaoId=%s".formatted(credito.dotacaoId().valor()), e.getMessage()));
            }
        }

        ResultadoLote<DotacaoId> resultadoInsercao = candidatasInsercao.isEmpty()
                ? new ResultadoLote<>(List.of(), List.of())
                : repositorio.inserirEmLote(candidatasInsercao);
        ResultadoLote<DotacaoId> resultadoCredito = candidatosCredito.isEmpty()
                ? new ResultadoLote<>(List.of(), List.of())
                : repositorio.aplicarCreditosEmLote(enteId, candidatosCredito);

        List<ErroItemLote> erros = new ArrayList<>(errosValidacao);
        erros.addAll(resultadoInsercao.erros());
        erros.addAll(resultadoCredito.erros());

        registrarAuditoria(
                enteId,
                usuarioAutenticado,
                resultadoInsercao.processados(),
                resultadoCredito.processados(),
                candidatosCredito,
                erros);

        return new Resultado(resultadoInsercao.processados(), resultadoCredito.processados(), erros);
    }

    private void registrarAuditoria(
            TenantId enteId,
            Sessao usuarioAutenticado,
            List<DotacaoId> inseridas,
            List<DotacaoId> atualizadas,
            List<CreditoAdicional> candidatosCredito,
            List<ErroItemLote> erros) {
        // Lei 4.320/1964 arts. 40-46: dotacao.valor_autorizado só guarda a soma; tipo/historico
        // (o ato legal que autorizou o crédito) só sobrevive aqui, na trilha imutável.
        Set<DotacaoId> aplicados = Set.copyOf(atualizadas);
        String creditosAplicados = candidatosCredito.stream()
                .filter(credito -> aplicados.contains(credito.dotacaoId()))
                .map(credito -> "%s:%s:%s".formatted(credito.dotacaoId().valor(), credito.tipo().codigo(), credito.historico()))
                .collect(Collectors.joining(";"));

        auditoria.append(new EventoAuditoria(
                enteId,
                "execucao_dotacao_ingerida",
                usuarioAutenticado.titular().numero(),
                "execucao:dotacao:lote",
                Instant.now(clock),
                Map.of(
                        "inseridas", String.valueOf(inseridas.size()),
                        "dotacoesInseridas", juntarIds(inseridas),
                        "atualizadas", String.valueOf(atualizadas.size()),
                        "dotacoesAtualizadas", juntarIds(atualizadas),
                        "creditosAdicionaisAplicados", creditosAplicados,
                        "erros", String.valueOf(erros.size()))));
    }

    private static String juntarIds(List<DotacaoId> ids) {
        return ids.stream().map(id -> id.valor().toString()).collect(Collectors.joining(","));
    }

    /** Item de entrada da Fixação (carga da LOA): dados de uma nova dotação a inserir. */
    public record SolicitacaoFixacaoDotacao(
            int exercicio,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            UnidadeGestoraId unidadeGestoraId,
            Dinheiro valorAutorizado) {

        public SolicitacaoFixacaoDotacao {
            Validacoes.exigirNaoNulo(classificacaoOrcamentaria, "classificacaoOrcamentaria");
            Validacoes.exigirNaoNulo(fonteRecurso, "fonteRecurso");
            Validacoes.exigirNaoNulo(unidadeGestoraId, "unidadeGestoraId");
            Validacoes.exigirNaoNulo(valorAutorizado, "valorAutorizado");
        }
    }

    /** Resultado do lote: dotações inseridas/atualizadas com sucesso + erros por item (ADR-0013). */
    public record Resultado(
            List<DotacaoId> dotacoesInseridas, List<DotacaoId> dotacoesAtualizadas, List<ErroItemLote> erros) {

        public Resultado {
            dotacoesInseridas = List.copyOf(dotacoesInseridas);
            dotacoesAtualizadas = List.copyOf(dotacoesAtualizadas);
            erros = List.copyOf(erros);
        }
    }
}
