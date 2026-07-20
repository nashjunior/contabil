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
 *   <li>{@link br.contabil.plataforma.infra.assinatura.PreparadorAssinaturaPades} —
 *       incorporação PAdES do PKCS#7 no PDF (RAZ-34): reserva o placeholder de
 *       assinatura (byte-range + dicionário {@code /Sig}, ISO 32000 §12.8) via
 *       Apache PDFBox, calcula o hash sobre esse byte-range (input real de
 *       {@code assinarPkcs7}) e, só depois, embute o CMS/PKCS#7 devolvido.</li>
 *   <li>{@link br.contabil.plataforma.infra.assinatura.ServicoAssinaturaGovBrAvancada} —
 *       orquestra elegibilidade (via 403 do provedor), preparo PAdES + hash SHA-256,
 *       assinatura, revogação, incorporação do CMS no PDF, manifesto e trilha
 *       ({@code AuditoriaEscrita}, RAZ-8).</li>
 * </ul>
 *
 * <h2>Lacunas conhecidas — não implementadas nesta issue</h2>
 * <ul>
 *   <li><b>Leitura/gravação do documento no object store</b> (ADR-0009) —
 *       resolvida: recebidas como colaboradores injetados ({@code Function}/
 *       {@code BiFunction}) porque o seam preexiste à decisão de tecnologia, mas
 *       hoje a composição real ({@code ServicoAssinaturaGovBrConfiguration}, RAZ-24)
 *       injeta os adapters concretos de {@code ObjectStoreSeamsAssinaturaConfiguration}
 *       (ADR-0018, RAZ-32/41/45).</li>
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
 * <p>O fluxo OAuth2 interativo do signatário (authorization_code + PKCE) fica na
 * borda web do {@code bootstrap}, conforme ADR-0017. Este pacote continua
 * recebendo apenas o {@code Supplier<String>} do token de acesso.
 *
 * <p>Fonte: Roteiro de Integração API Assinatura avançada gov.br —
 * https://manual-integracao-assinatura-eletronica.servicos.gov.br/
 */
package br.contabil.plataforma.infra.assinatura;
