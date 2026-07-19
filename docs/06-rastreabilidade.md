# Rastreabilidade legal → requisito

[← Índice](./README.md)

| Base (Decreto 10.540/2020) | Exigência | Requisito de produto |
| --- | --- | --- |
| Art. 2º (único/integrado) | Base compartilhada; integração sem intervenção humana | Base única; conectores automáticos |
| Art. 3º (tempo real) | Publicação até o 1º dia útil subsequente | Pipeline registro → transparência com SLA |
| Art. 3º–4º | Partidas dobradas, analítica, moeda/idioma nacionais | Motor contábil de dupla entrada; documento de suporte |
| Art. 4º, §10 | Proibir backdating e reprocessamento | Travas de data e de reprocessamento |
| Art. 5º | Correção por novos registros | Estorno/retificação; original imutável |
| Art. 6º | Prazos e bloqueio pós-encerramento | Calendário contábil; trava de período |
| Art. 7º–8º | Transparência pormenorizada; e-MAG; LGPD | Portal detalhado, acessível, com sigilo |
| Art. 9º | Integridade, auditabilidade, disponibilidade | Controles de integridade; identificação de versão do sistema; alvo de disponibilidade (uptime) e continuidade (RPO/RTO, plano de DR/BCP) |
| Art. 10 | Interoperabilidade (ePING) | APIs/formatos aderentes ao ePING |
| Art. 11 | Acesso por CPF/certificado; sem genéricos | Identidade individual; onboarding com chefia |
| Art. 12 | Log de inclusão/alteração/exclusão | Trilha append-only/WORM com encadeamento de hash ancorado em (numero_sequencial cronológico, data-hora de registro do servidor), em store de auditoria segregado por custódia e por ente — sem privilégio de escrita/exclusão dos administradores de negócio; replicação para destino externo; verificação periódica de integridade da cadeia; retenção parametrizável alinhada à guarda contábil/fiscal, nunca inferior à dos registros que audita. Trilha pesquisável pelo controle interno. |
| Art. 13–14 | Conexão segura; base restrita | TLS; acesso privilegiado nominal e logado |
| Art. 15 | Cópia de segurança (preferência diária) | Backup automatizado, cifrado em repouso (chaves geridas fora de produção), com ao menos uma cópia imutável/air-gapped contra ransomware, redundância geográfica offsite, restauração por ente, testes de restauração periódicos com evidência e log de acesso ao backup como dado sensível |
| Art. 16 / art. 51 LRF | Requisitos adicionais para consolidação | Extratores parametrizáveis (SICONFI) |

> Dispositivo de "tempo real" padronizado com a spec de transparência (Decreto 10.540/2020, art. 3º); revalidar a redação vigente na fonte oficial.
>
> Os requisitos não-funcionais de operação (disponibilidade, RPO/RTO, DR/BCP, teste de restauração) devem ser consolidados em spec própria de NFR — ver pendência de escopo.

## Rastreabilidade — normas transversais e financeiras

| Norma | Exigência | Requisito de produto | Spec |
| --- | --- | --- | --- |
| Lei 4.320/1964 | Estágios da despesa; restos a pagar (arts. 36–37) | Modelo de dados/execução com empenho, liquidação e pagamento; controle de restos a pagar | Modelo de dados/execução |
| LRF (LC 101/2000) | Vedação de despesa em fim de mandato (art. 42); RREO/RGF (arts. 52–55); transparência (art. 48/48-A); sanção (art. 73-B/73-C → 23) | Travas de fim de mandato; geração de RREO/RGF; publicidade e prazos; controles de sanção | Regras de negócio; transparência |
| Lei 14.063/2020 | Assinatura eletrônica | Integração de assinatura | transversais/01 |
| Lei 14.133/2021 (art. 174/94) | Publicação no PNCP | Conector PNCP | transversais/02 |
| LAI 12.527/2011 + Lei 14.129/2021 | Dados abertos | Publicação em dados abertos | transversais/03 |
| LGPD 13.709/2018 | Proteção de dados pessoais | Controles de privacidade e sigilo | transversais/04 |
| LBI 13.146/2015 + WCAG/NBR 17225 | Acessibilidade | Conformidade de acessibilidade | transversais/05 |

> A matriz completa está no **PRD** (seção 8).

---

[← Regras de negócio](./05-regras-de-negocio.md) · [Índice](./README.md) · [Roadmap →](./07-roadmap.md)
