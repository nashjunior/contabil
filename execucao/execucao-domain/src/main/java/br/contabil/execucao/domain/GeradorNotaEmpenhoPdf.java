package br.contabil.execucao.domain;

/**
 * Porta de geração do conteúdo PDF/A da nota de empenho a partir do agregado
 * (ADR-0027 (b)/item 3) — implementação PDFBox em {@code execucao-infra}. Gera
 * só o conteúdo do documento; o placeholder/hash de assinatura (ISO 32000-1
 * §12.8) é responsabilidade de {@code ServicoAssinatura}, não deste port.
 */
public interface GeradorNotaEmpenhoPdf {

    byte[] gerar(Empenho empenho);
}
