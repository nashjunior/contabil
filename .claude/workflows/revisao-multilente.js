export const meta = {
  name: 'revisao-multilente',
  description: 'Auditoria multi-lente (Seguranca, Produto, Arquiteto, Conformidade) da documentacao do SIAFIC: revisao independente -> replica cruzada -> reconciliacao. REPORT-ONLY: nao edita arquivos, so reporta o changeset sugerido e as tensoes para decisao humana.',
  whenToUse: 'Reauditar a documentacao do SIAFIC apos mudancas relevantes. Passe args = [caminhos] para restringir o escopo; sem args, audita o conjunto substantivo padrao.',
  phases: [
    { title: 'Revisão' },
    { title: 'Réplica cruzada' },
    { title: 'Reconciliação' },
  ],
}

const BASE = '/Volumes/Nash HD/projects/Oberware/contabil/docs'
const DEFAULT_DOCS = [
  `${BASE}/02-base-legal.md`,
  `${BASE}/03-arquitetura.md`,
  `${BASE}/04-fluxos.md`,
  `${BASE}/05-regras-de-negocio.md`,
  `${BASE}/06-rastreabilidade.md`,
  `${BASE}/07-roadmap.md`,
  `${BASE}/10-modelo-dados.md`,
  `${BASE}/11-plataforma-transversal.md`,
  `${BASE}/12-migracao.md`,
  `${BASE}/13-nfr-e-operacao.md`,
  `${BASE}/transversais/01-assinatura-eletronica.md`,
  `${BASE}/transversais/02-pncp.md`,
  `${BASE}/transversais/03-transparencia.md`,
  `${BASE}/transversais/04-lgpd.md`,
  `${BASE}/transversais/05-acessibilidade.md`,
  `${BASE}/arquitetura-tecnica/README.md`,
  `${BASE}/arquitetura-tecnica/razao-contabil-schema.md`,
]
const DOCS = (Array.isArray(args) && args.length) ? args : DEFAULT_DOCS
const DOCLIST = DOCS.map(d => `- ${d}`).join('\n')

const CTX = `O produto e um SIAFIC (Sistema Unico e Integrado de Execucao Orcamentaria, Administracao Financeira e Controle) para estados e municipios brasileiros, conforme Decreto 10.540/2020. Nucleo = contabil-orcamentario-financeiro + transparencia + saida SICONFI; licitacoes/patrimonio/folha/arrecadacao sao ESTRUTURANTES que integram por fora. Requisitos transversais (assinatura, PNCP, transparencia, LGPD, acessibilidade) sao servicos de PLATAFORMA. Stack decidida: JVM (Java/Kotlin). Convencoes: cada arquivo comeca com H1 e back-link; tabelas usam "| --- |"; rotulos Mermaid ASCII sem acento; marcacoes [OBRIGATORIO]/[PRODUTO].`

const LENSES = [
  { key: 'seguranca', title: 'Seguranca', prefix: 'SEG', focus: 'LGPD na pratica, ICP-Brasil/assinatura, RBAC e segregacao de funcoes, superficie de ataque, trilha imutavel, gestao de segredos, incidentes, backup/DR, criptografia, mascaramento de PII no portal.' },
  { key: 'produto', title: 'Produto', prefix: 'PRD', focus: 'escopo do MVP vs gold-plating, o que cortar/adiar, priorizacao, faseamento realista, clareza/testabilidade dos requisitos, coerencia com mercado e roadmap, foco no que reprova/aprova no controle externo.' },
  { key: 'arquiteto', title: 'Arquiteto', prefix: 'ARQ', focus: 'base unica fonte da verdade, servicos de plataforma e contratos, acoplamento nucleo x estruturantes, modelo de dados/cardinalidades, integracoes, idempotencia/outbox, consistencia entre fluxos e modelo, coerencia com os ADRs.' },
  { key: 'conformidade', title: 'Conformidade', prefix: 'CNF', focus: 'aderencia a Lei 4.320/64, LRF, Decreto 10.540/2020, PCASP/MCASP/DCASP, prazos e remessas SICONFI-MSC/TCE, EXATIDAO das citacoes legais (numeros de artigos), lacunas normativas.' },
]

const FINDINGS_SCHEMA = {
  type: 'object',
  properties: {
    lens: { type: 'string' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          file: { type: 'string' },
          section: { type: 'string' },
          issue: { type: 'string' },
          severity: { type: 'string', enum: ['alta', 'media', 'baixa'] },
          category: { type: 'string' },
          proposed_change: { type: 'string' },
        },
        required: ['id', 'file', 'issue', 'severity', 'proposed_change'],
      },
    },
  },
  required: ['lens', 'findings'],
}

const REACTIONS_SCHEMA = {
  type: 'object',
  properties: {
    lens: { type: 'string' },
    reactions: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          finding_id: { type: 'string' },
          stance: { type: 'string', enum: ['concordo', 'discordo', 'refino'] },
          rationale: { type: 'string' },
          revised_change: { type: 'string' },
        },
        required: ['finding_id', 'stance', 'rationale'],
      },
    },
    new_conflicts: {
      type: 'array',
      items: { type: 'object', properties: { about: { type: 'string' }, positions: { type: 'string' } }, required: ['about', 'positions'] },
    },
  },
  required: ['lens', 'reactions'],
}

const CHANGESET_SCHEMA = {
  type: 'object',
  properties: {
    edits_by_file: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          changes: {
            type: 'array',
            items: {
              type: 'object',
              properties: { summary: { type: 'string' }, instruction: { type: 'string' }, source_lenses: { type: 'string' } },
              required: ['summary', 'instruction'],
            },
          },
        },
        required: ['file', 'changes'],
      },
    },
    unresolved: {
      type: 'array',
      items: {
        type: 'object',
        properties: { topic: { type: 'string' }, options: { type: 'string' }, recommendation: { type: 'string' } },
        required: ['topic', 'options'],
      },
    },
  },
  required: ['edits_by_file', 'unresolved'],
}

phase('Revisão')
const reviews = await parallel(LENSES.map(l => () =>
  agent(
    `Voce e um revisor senior na lente de ${l.title} de um produto SIAFIC. ${CTX}\n\n` +
    `Tarefa: revisar CRITICAMENTE os documentos abaixo pela otica de ${l.title}. Use Read em cada arquivo antes de opinar.\n` +
    `Foco de ${l.title}: ${l.focus}\n\n` +
    `Documentos no escopo:\n${DOCLIST}\n\n` +
    `Achados objetivos e acionaveis (id com prefixo ${l.prefix}-). Qualidade sobre quantidade; nao duplique o que ja esta correto; aponte lacunas e imprecisoes. Retorne no schema.`,
    { label: `revisao:${l.key}`, phase: 'Revisão', agentType: 'general-purpose', schema: FINDINGS_SCHEMA }
  )
))
const findings = reviews.filter(Boolean)
const totalFindings = findings.reduce((n, f) => n + (f.findings ? f.findings.length : 0), 0)
log(`Revisao: ${findings.length} lentes, ${totalFindings} achados`)

phase('Réplica cruzada')
const allFindingsJson = JSON.stringify(findings)
const reactions = await parallel(LENSES.map(l => () =>
  agent(
    `Voce e o revisor da lente ${l.title} no DEBATE de auditoria do SIAFIC. ${CTX}\n\n` +
    `Abaixo, TODOS os achados das 4 lentes. Reaja aos das OUTRAS lentes (e refine os seus): concordo/discordo/refino, com justificativa; quando refinar, de a mudanca revisada. Aponte CONFLITOS reais entre lentes.\n\n` +
    `Achados: ${allFindingsJson}\n\nRetorne no schema.`,
    { label: `replica:${l.key}`, phase: 'Réplica cruzada', agentType: 'general-purpose', schema: REACTIONS_SCHEMA }
  )
))
const reacts = reactions.filter(Boolean)

phase('Reconciliação')
const changeset = await agent(
  `Voce e o LIDER TECNICO reconciliador da auditoria do SIAFIC. ${CTX}\n\n` +
  `Recebe os achados das 4 lentes e as replicas. Consolide num CHANGESET SUGERIDO (report-only; nao ha aplicacao automatica).\n` +
  `Regras:\n` +
  `- Agrupe por ARQUIVO (um objeto por arquivo, caminho absoluto exato do escopo). Cada mudanca: summary + instrucao precisa e autossuficiente.\n` +
  `- So mudancas de ALTA confianca/consenso (erro factual, citacao legal errada, lacuna critica, inconsistencia entre docs).\n` +
  `- TENSAO genuina entre lentes (tradeoff sem resposta objetiva) vai em "unresolved" com opcoes e recomendacao.\n` +
  `- Ajustes cirurgicos; preserve convencoes (H1, "| --- |", Mermaid ASCII, [OBRIGATORIO]/[PRODUTO]).\n\n` +
  `Caminhos:\n${DOCLIST}\n\n` +
  `Achados: ${JSON.stringify(findings)}\n\nReplicas: ${JSON.stringify(reacts)}\n\nRetorne no schema.`,
  { label: 'reconciliacao', phase: 'Reconciliação', effort: 'high', schema: CHANGESET_SCHEMA }
)

return {
  modo: 'report-only',
  lentes: findings.map(f => ({ lens: f.lens, n: f.findings ? f.findings.length : 0 })),
  sugestoes_por_arquivo: (changeset && changeset.edits_by_file) ? changeset.edits_by_file : [],
  tensoes_nao_resolvidas: (changeset && changeset.unresolved) ? changeset.unresolved : [],
}
