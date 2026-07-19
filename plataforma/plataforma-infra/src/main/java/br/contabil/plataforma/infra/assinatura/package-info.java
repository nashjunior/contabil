/**
 * Implementação F0 de {@link br.contabil.plataforma.domain.assinatura.ServicoAssinatura}
 * (RAZ-11): provedor único gov.br avançada, PAdES/PDF via PKCS#7, checagem de
 * revogação, manifesto e trilha (transversais/01-assinatura-eletronica.md).
 *
 * <h2>O que está implementado de verdade</h2>
 * <ul>
 *   <li>{@link br.contabil.plataforma.infra.assinatura.ProvedorAssinaturaGovBrHttp} —
 *       chamada HTTP real à operação {@code assinarPKCS7} da API gov.br (staging),
 *       request/response conferidos contra o manual oficial de integração.</li>
 *   <li>{@link br.contabil.plataforma.infra.assinatura.VerificadorRevogacaoCertificadoPkix} —
 *       checagem OCSP/CRL via {@code CertPathValidator("PKIX")} padrão do JDK,
 *       deny-by-default (falha ou indeterminação = não seguro para assinar).</li>
 *   <li>{@link br.contabil.plataforma.infra.assinatura.ServicoAssinaturaGovBrAvancada} —
 *       orquestra elegibilidade (via 403 do provedor), hash SHA-256, assinatura,
 *       revogação, manifesto e trilha ({@code AuditoriaEscrita}, RAZ-8).</li>
 * </ul>
 *
 * <h2>Lacunas conhecidas — não implementadas nesta issue</h2>
 * <ul>
 *   <li><b>Fluxo OAuth2 interativo</b> (authorization_code, redirect do signatário
 *       ao gov.br) — exige camada web/BFF que ainda não existe no projeto (só há
 *       domínio/infra de backend). {@code ProvedorAssinaturaGovBrHttp} recebe um
 *       token já obtido via {@code Supplier<String>}.</li>
 *   <li><b>Leitura/gravação do documento no object store</b> (ADR-0009) — recebidas
 *       como colaboradores injetados ({@code Function}/{@code BiFunction}) em vez de
 *       um adapter concreto, porque o serviço de object store/GED ainda não tem
 *       issue própria implementada.</li>
 *   <li><b>Incorporação PAdES no PDF</b> — a API gov.br devolve PKCS#7 destacado
 *       (.p7s); embutir isso como assinatura PAdES no PDF (byte-range, dicionário
 *       de assinatura ISO 32000) exige uma biblioteca de PDF (ex.: PDFBox/iText)
 *       que ainda não é dependência do projeto. {@code publicadorDocumentoAssinado}
 *       recebe o PKCS#7 bruto; quem o injetar decide como/se embute no PDF.</li>
 *   <li><b>Propagação do tenant corrente</b> — resolvida: o {@code TenantId ente}
 *       vem explícito no {@code DocumentoParaAssinar} (ADR-0015), não de um
 *       {@code TenantContext} ambiente; o seam {@code resolvedorDeEnte} (RAZ-11)
 *       foi removido (RAZ-28).</li>
 *   <li><b>Âncoras de confiança ICP-Brasil</b> — {@code VerificadorRevogacaoCertificadoPkix}
 *       não empacota o bundle de CA raiz/intermediária; é config/implantação.</li>
 *   <li><b>F1 explicitamente fora de escopo</b>: ICP-Brasil qualificada, workflow
 *       multi-assinatura, validação completa via Validador do ITI, PAdES-LTV +
 *       carimbo de tempo.</li>
 * </ul>
 *
 * <p>Fonte: Roteiro de Integração API Assinatura avançada gov.br —
 * https://manual-integracao-assinatura-eletronica.servicos.gov.br/
 */
package br.contabil.plataforma.infra.assinatura;
