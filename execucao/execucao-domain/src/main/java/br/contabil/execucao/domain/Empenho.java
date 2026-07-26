package br.contabil.execucao.domain;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

/**
 * Agregado de empenho (Lei 4.320/1964 art. 58-60): compromete crédito da
 * dotação para um credor e gera um fato contábil append-only no razão.
 *
 * <p>F1 cobre só o registro inicial (emissão) — reforço, anulação e o ciclo de
 * vida completo (execucao-orcamentaria-despesa.md §Ciclo de vida) ficam para
 * issues próprias que consomem este agregado.
 *
 * <p>{@link #status} é o estado forte do <b>documento</b> (nota de empenho,
 * ADR-0027) — ortogonal ao comprometimento contábil, que já é efetivo em
 * {@link StatusEmpenho#REGISTRADO} (R1). Transiciona só por {@link
 * #marcarPendenteAssinatura}, {@link #assinar} e {@link #rejeitarAssinatura};
 * o agregado segue imutável, reconstruído via repositório.
 */
public final class Empenho {

    private final EmpenhoId id;
    private final TenantId enteId;
    private final long numeroSequencial;
    private final int exercicio;
    private final TipoEmpenho tipo;
    private final DotacaoId dotacaoId;
    private final CredorId credorId;
    private final UnidadeGestoraId unidadeGestoraId;
    private final ContratoId contratoId;
    private final Dinheiro valor;
    private final LocalDate dataFato;
    private final String classificacaoOrcamentaria;
    private final String fonteRecurso;
    private final String historico;
    private final UUID fatoContabilId;
    private final Cpf autor;
    private final StatusEmpenho status;
    private final Optional<URI> documentoPendenteUri;
    private final Optional<DocumentoAssinadoEmpenho> documentoAssinado;

    private Empenho(
            EmpenhoId id,
            TenantId enteId,
            long numeroSequencial,
            int exercicio,
            TipoEmpenho tipo,
            DotacaoId dotacaoId,
            CredorId credorId,
            UnidadeGestoraId unidadeGestoraId,
            ContratoId contratoId,
            Dinheiro valor,
            LocalDate dataFato,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            String historico,
            UUID fatoContabilId,
            Cpf autor,
            StatusEmpenho status,
            Optional<URI> documentoPendenteUri,
            Optional<DocumentoAssinadoEmpenho> documentoAssinado) {
        this.id = id;
        this.enteId = enteId;
        this.numeroSequencial = numeroSequencial;
        this.exercicio = exercicio;
        this.tipo = tipo;
        this.dotacaoId = dotacaoId;
        this.credorId = credorId;
        this.unidadeGestoraId = unidadeGestoraId;
        this.contratoId = contratoId;
        this.valor = valor;
        this.dataFato = dataFato;
        this.classificacaoOrcamentaria = classificacaoOrcamentaria;
        this.fonteRecurso = fonteRecurso;
        this.historico = historico;
        this.fatoContabilId = fatoContabilId;
        this.autor = autor;
        this.status = status;
        this.documentoPendenteUri = documentoPendenteUri;
        this.documentoAssinado = documentoAssinado;
    }

    public static Empenho registrar(
            EmpenhoId id,
            TenantId enteId,
            long numeroSequencial,
            int exercicio,
            TipoEmpenho tipo,
            DotacaoId dotacaoId,
            CredorId credorId,
            UnidadeGestoraId unidadeGestoraId,
            ContratoId contratoId,
            Dinheiro valor,
            LocalDate dataFato,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            String historico,
            UUID fatoContabilId,
            Cpf autor) {
        return reconstituir(
                id,
                enteId,
                numeroSequencial,
                exercicio,
                tipo,
                dotacaoId,
                credorId,
                unidadeGestoraId,
                contratoId,
                valor,
                dataFato,
                classificacaoOrcamentaria,
                fonteRecurso,
                historico,
                fatoContabilId,
                autor,
                StatusEmpenho.REGISTRADO,
                Optional.empty(),
                Optional.empty());
    }

    /** Reconstrução a partir do repositório (inclui o estado forte do documento) — nunca validação de entrada nova. */
    public static Empenho reconstituir(
            EmpenhoId id,
            TenantId enteId,
            long numeroSequencial,
            int exercicio,
            TipoEmpenho tipo,
            DotacaoId dotacaoId,
            CredorId credorId,
            UnidadeGestoraId unidadeGestoraId,
            ContratoId contratoId,
            Dinheiro valor,
            LocalDate dataFato,
            String classificacaoOrcamentaria,
            String fonteRecurso,
            String historico,
            UUID fatoContabilId,
            Cpf autor,
            StatusEmpenho status,
            Optional<URI> documentoPendenteUri,
            Optional<DocumentoAssinadoEmpenho> documentoAssinado) {
        validarValor(valor, "valor do empenho");
        return new Empenho(
                Objects.requireNonNull(id, "id não pode ser nulo"),
                Objects.requireNonNull(enteId, "enteId não pode ser nulo"),
                numeroSequencial,
                exercicio,
                Objects.requireNonNull(tipo, "tipo não pode ser nulo"),
                Objects.requireNonNull(dotacaoId, "dotacaoId não pode ser nulo"),
                Objects.requireNonNull(credorId, "credorId não pode ser nulo"),
                Objects.requireNonNull(unidadeGestoraId, "unidadeGestoraId não pode ser nulo"),
                contratoId,
                valor,
                Objects.requireNonNull(dataFato, "dataFato não pode ser nula"),
                textoObrigatorio(classificacaoOrcamentaria, "classificação orçamentária"),
                textoObrigatorio(fonteRecurso, "fonte de recurso"),
                textoObrigatorio(historico, "histórico"),
                Objects.requireNonNull(fatoContabilId, "fatoContabilId não pode ser nulo"),
                Objects.requireNonNull(autor, "autor não pode ser nulo"),
                Objects.requireNonNull(status, "status não pode ser nulo"),
                Objects.requireNonNull(documentoPendenteUri, "documentoPendenteUri (Optional) não pode ser nulo"),
                Objects.requireNonNull(documentoAssinado, "documentoAssinado (Optional) não pode ser nulo"));
    }

    /**
     * Transição {@code REGISTRADO -> PENDENTE_ASSINATURA} (ADR-0027 (b)): o worker
     * já gerou e armazenou o PDF/A da nota, {@code documentoPendenteUri} é a
     * referência no object store (ADR-0009).
     */
    public Empenho marcarPendenteAssinatura(URI documentoPendenteUri) {
        exigirStatus(StatusEmpenho.REGISTRADO);
        Objects.requireNonNull(documentoPendenteUri, "documentoPendenteUri não pode ser nulo");
        return new Empenho(
                id, enteId, numeroSequencial, exercicio, tipo, dotacaoId, credorId, unidadeGestoraId, contratoId,
                valor, dataFato, classificacaoOrcamentaria, fonteRecurso, historico, fatoContabilId, autor,
                StatusEmpenho.PENDENTE_ASSINATURA, Optional.of(documentoPendenteUri), Optional.empty());
    }

    /**
     * Transição {@code PENDENTE_ASSINATURA|ASSINATURA_REJEITADA -> ASSINADO}
     * (ADR-0027 (c)) — guardada por update condicional no repositório (R3); este
     * método só reforça a mesma regra em memória (defesa em profundidade, não
     * substitui a trava do banco sob concorrência).
     */
    public Empenho assinar(DocumentoAssinadoEmpenho documentoAssinado) {
        exigirStatus(StatusEmpenho.PENDENTE_ASSINATURA, StatusEmpenho.ASSINATURA_REJEITADA);
        Objects.requireNonNull(documentoAssinado, "documentoAssinado não pode ser nulo");
        return new Empenho(
                id, enteId, numeroSequencial, exercicio, tipo, dotacaoId, credorId, unidadeGestoraId, contratoId,
                valor, dataFato, classificacaoOrcamentaria, fonteRecurso, historico, fatoContabilId, autor,
                StatusEmpenho.ASSINADO, documentoPendenteUri, Optional.of(documentoAssinado));
    }

    /**
     * Transição {@code PENDENTE_ASSINATURA -> ASSINATURA_REJEITADA} (ADR-0027
     * R2): a tentativa de assinatura foi recusada pelo provedor (certificado
     * inválido/revogado) — retrabalho, o crédito segue comprometido, aguarda
     * nova tentativa. Nunca toca o razão.
     */
    public Empenho rejeitarAssinatura() {
        exigirStatus(StatusEmpenho.PENDENTE_ASSINATURA);
        return new Empenho(
                id, enteId, numeroSequencial, exercicio, tipo, dotacaoId, credorId, unidadeGestoraId, contratoId,
                valor, dataFato, classificacaoOrcamentaria, fonteRecurso, historico, fatoContabilId, autor,
                StatusEmpenho.ASSINATURA_REJEITADA, documentoPendenteUri, Optional.empty());
    }

    private void exigirStatus(StatusEmpenho... permitidos) {
        for (StatusEmpenho permitido : permitidos) {
            if (status == permitido) {
                return;
            }
        }
        throw new EmpenhoAssinaturaConflitanteException(
                "empenho %s está em status %s, incompatível com a transição pedida".formatted(id.valor(), status));
    }

    public static void validarValor(Dinheiro valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo");
        if (valor.compareTo(Dinheiro.zero()) <= 0) {
            throw new ExecucaoInvalidaException("valor_invalido", campo + " deve ser positivo");
        }
    }

    static String textoObrigatorio(String valor, String campo) {
        Objects.requireNonNull(valor, campo + " não pode ser nulo");
        if (valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode ser vazio");
        }
        return valor;
    }

    public EmpenhoId id() {
        return id;
    }

    public TenantId enteId() {
        return enteId;
    }

    public long numeroSequencial() {
        return numeroSequencial;
    }

    public int exercicio() {
        return exercicio;
    }

    public TipoEmpenho tipo() {
        return tipo;
    }

    public DotacaoId dotacaoId() {
        return dotacaoId;
    }

    public CredorId credorId() {
        return credorId;
    }

    public UnidadeGestoraId unidadeGestoraId() {
        return unidadeGestoraId;
    }

    public ContratoId contratoId() {
        return contratoId;
    }

    public Dinheiro valor() {
        return valor;
    }

    public LocalDate dataFato() {
        return dataFato;
    }

    public String classificacaoOrcamentaria() {
        return classificacaoOrcamentaria;
    }

    public String fonteRecurso() {
        return fonteRecurso;
    }

    public String historico() {
        return historico;
    }

    public UUID fatoContabilId() {
        return fatoContabilId;
    }

    /** CPF de quem registrou o empenho — checado pelo gate de aprovação (ADR-0023, veda auto-aprovação). */
    public Cpf autor() {
        return autor;
    }

    /** Estado forte do documento (nota de empenho) — ortogonal ao comprometimento contábil (R1). */
    public StatusEmpenho status() {
        return status;
    }

    /** Referência do PDF/A pendente de assinatura no object store (ADR-0009), presente a partir de {@code PENDENTE_ASSINATURA}. */
    public Optional<URI> documentoPendenteUri() {
        return documentoPendenteUri;
    }

    /** {@code DOCUMENTO_ASSINADO}, presente só em {@code ASSINADO}. */
    public Optional<DocumentoAssinadoEmpenho> documentoAssinado() {
        return documentoAssinado;
    }
}
