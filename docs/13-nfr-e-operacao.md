# Requisitos não-funcionais e operação

[← Índice](./README.md)

> O **“como opera”** do produto (não o “o que faz”): disponibilidade, continuidade, desempenho, retenção e o **piso de segurança inegociável**. Consolida exigências do **Decreto 10.540/2020 (arts. 9º e 15)** e os achados de segurança da [revisão multi-lente](./11-plataforma-transversal.md). Referenciado pela [rastreabilidade](./06-rastreabilidade.md).

## Disponibilidade e continuidade (Decreto 10.540 art. 9º e 15)

- **Uptime alvo** parametrizável por contrato (SLA); o portal de transparência é serviço público contínuo.
- **RPO/RTO** definidos e testados (perda máxima de dados / tempo máximo de retomada).
- **Backup** cifrado, com **cópia imutável/air-gapped**, redundância geográfica e **teste de restauração periódico** (backup só vale se a restauração for comprovada — art. 15 prefere cadência diária).
- **DR/BCP** — plano de recuperação de desastre e continuidade de negócio, exercitado.

## Desempenho e escala

- **SLA de latência da transparência:** publicação **≤ 1º dia útil** após o registro ([transparência](./transversais/03-transparencia.md)).
- **Multi-ente** com isolamento (ver decisão de tenancy em [arquitetura](./03-arquitetura.md)); absorver picos (fechamento, prazos LRF) sem degradar.
- **Controles de borda** na API pública (rate limiting, quotas, cache/CDN, anti-DDoS) que preservem a disponibilidade sem exigir cadastro.

## Piso de segurança F0

Conjunto **inegociável** no F0/MVP — o corte de escopo pode adiar riqueza funcional, **nunca** um controle que protege movimentação de recurso ou dado pessoal. Escala-se o que é *enterprise*; não se abre mão do piso.

| `[PISO-SEGURANCA-F0]` — obrigatório no F0 | Escalona (F1/F2) |
| --- | --- |
| **MFA** para perfis que movimentam recurso (ordenador, tesouraria, admin) | MFA generalizado a todos os perfis |
| **Cofre de segredos** + cifra de credenciais/dados bancários (KMS/HSM; sem segredo em código) | Rotação automática, HSM dedicado |
| **Constraints estruturais no banco** (integridade referencial, saldos ≥ 0, invariante Σdébito=Σcrédito) | — |
| **Trilha hash-chain** em store segregado (append-only/WORM) | Replicação externa + verificação periódica de integridade |
| **Backup cifrado + teste de restauração** | DR/air-gap/BCP avançado, cópia geograficamente redundante |
| **Detecção mínima de anomalia** de acesso | **PAM completo** (JIT, dual control, replay de sessão) |
| **TLS em todas as interfaces**; hashing forte de senha (Argon2id/bcrypt/scrypt + salt) | Criptografia em repouso ampla |
| **Proibição de PII real em não-produção** (dados mascarados/sintéticos) | Log de leitura de **toda** PII |
| **Segregação de funções** com veto preventivo (sem auto-aprovação / acúmulo conflitante) | Recertificação periódica de acessos automatizada |

> **Regra:** para o *wedge* de municípios pequenos, o piso acima é o teto de custo aceitável no F0; controles *enterprise* entram por **tier/fase** conforme o porte do ente e o valor movimentado.

## Retenção e trilha

- **Retenção parametrizável** alinhada à **guarda contábil/fiscal** (varia por norma do ente/TCE — não fixar em código).
- Trilha de auditoria (inclusão/alteração/estorno **e leitura/exportação** de dados pessoais e sigilosos) imutável e pesquisável ([fluxo 7](./04-fluxos.md#7-trilha-de-auditoria-e-vedações)).
- Resposta a incidentes: detecção + pacote de evidências para o prazo da **Resolução CD/ANPD nº 15/2024** (a comunicação à ANPD é do ente — [LGPD](./transversais/04-lgpd.md)).

## Faseamento

| Fase | Entrega |
| --- | --- |
| **F0** | `[PISO-SEGURANCA-F0]` completo; backup cifrado + teste de restauração; TLS; trilha hash-chain; SLA de latência da transparência |
| **F1** | RPO/RTO testados; criptografia em repouso ampla; MFA generalizado; controles de borda na API; detecção de anomalia ampliada |
| **F2** | DR/air-gap/BCP exercitado; PAM completo; log de leitura de toda PII; redundância geográfica; recertificação automatizada |

## Fontes

- Decreto 10.540/2020, arts. 9º (integridade/disponibilidade) e 15 (backup) — [base legal](./02-base-legal.md).
- LGPD arts. 46–49 (segurança) · Resolução CD/ANPD nº 15/2024 (incidentes) — ver [LGPD](./transversais/04-lgpd.md).

> Ressalva: metas de uptime, RPO/RTO e prazos de retenção são **parametrizáveis por contrato/ente** — os valores concretos entram na negociação, não no código.

---

[← Migração e implantação](./12-migracao.md) · [Índice](./README.md)
