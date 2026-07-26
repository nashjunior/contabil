# ADR-0024 · Cofre de segredos F0 via passthrough de ambiente

- **Status:** Aceita
- **Data:** 2026-07-26
- **Contexto relacionado:** RAZ-75, ADR-0014 (contratos de plataforma como ports),
  ADR-0017 (BFF OAuth2 assinatura gov.br), ADR-0020 (piso operacional F0),
  `docs/11-plataforma-transversal.md` e `docs/13-nfr-e-operacao.md`.

## Contexto

O piso F0 exige que senha, token, chave privada e material de cifra **nunca** entrem
em código, repositório ou configuração versionada. Ao mesmo tempo, a aceitação F0
do produto não deve depender de provisionar KMS/HSM gerenciado para cada ente: isso
eleva custo operacional e amarra o deploy a um provedor antes do MVP.

Antes desta decisão, parte da documentação colocava `KMS/HSM` dentro do obrigatório
F0. Isso criava uma promessa maior do que a implementação aceita: o código já
materializa o port `CofreSegredos` (ADR-0014) e um adapter que resolve referências
lógicas por variáveis de ambiente, bloqueando valor literal direto quando existe
`*-ref`.

## Decisão

No F0, o requisito obrigatório é o **contrato de cofre** mais um adapter de
**passthrough de ambiente/arquivos de segredo da esteira**:

- O domínio e os módulos consomem apenas `CofreSegredos`; não leem `System.getenv`
  nem propriedades com valor secreto diretamente.
- A configuração versionada guarda somente referências (`cofre://...` ou `env:...`),
  nunca o valor do segredo.
- O adapter F0 mapeia a referência para variável de ambiente provida pelo deploy
  (`DB_RUNTIME_PASSWORD`, `GOVBR_ASSINATURA_OAUTH_CLIENT_SECRET`, etc.) ou por
  mecanismo equivalente de secret file montado pela esteira.
- Se a referência existe e o valor resolvido falta, o fluxo falha fechado. Não há
  fallback para senha/token literal.
- Rotação manual e troca sob incidente são operacionais no F0: o operador atualiza o
  segredo na esteira/cofre externo do ente e reinicia/recarrega o serviço conforme o
  runbook. Rotação automática, versionamento nativo e auditoria de uso no provedor
  são evolução.

KMS, HSM dedicado, Vault/Secrets Manager gerenciado e rotação automática ficam como
**F1/F2 ou tier enterprise**, sem alterar o port `CofreSegredos`. Quando existirem,
entram como outro adapter Spring para a mesma porta.

## Consequências

- **+** F0 fica auditável e barato: sem segredo em código/config versionada, com
  fail-closed e privilégio mínimo por escopo lógico.
- **+** O código não fica acoplado a AWS/Azure/GCP/Vault; a troca para KMS/HSM é
  mudança de adapter, não de domínio.
- **+** A documentação deixa de prometer HSM/KMS gerenciado como requisito de aceite
  do F0.
- **-** O F0 não entrega rotação automática nem auditoria nativa de uso no provedor
  de segredos; a evidência de rotação é operacional.
- **-** A esteira de deploy do ente precisa garantir que variáveis/secret files
  venham de fonte controlada e não sejam logadas.

## Alternativas consideradas

- **KMS/HSM gerenciado obrigatório no F0** — rejeitado: aumenta custo e dependência
  de provedor antes do MVP, apesar de continuar desejável para tiers maiores.
- **Ler variáveis de ambiente diretamente nos adapters de negócio** — rejeitado:
  espalha segredo pela borda e quebra o contrato único de plataforma.
- **Guardar segredo cifrado em `application.yml`** — rejeitado: ainda versiona
  material sensível e cria problema de chave para decifrar a própria configuração.

---

[← ADRs](./README.md)
