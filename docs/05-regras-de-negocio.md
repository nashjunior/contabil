# Regras de negócio (invioláveis)

[← Índice](./README.md)

Impostas pelo **sistema**, não delegadas à disciplina do usuário:

1. Proibida a **dupla escrituração** do mesmo fato — um fato, um conjunto de lançamentos na base única.
2. Distinguir **data de competência** (segue o fato gerador, Lei 4.320 art. 35 — pode ser retroativa dentro do período aberto) da **data-hora de registro** (relógio do servidor, imutável). Vedado registrar/alterar fato em **período encerrado** e vedado alterar o **timestamp de registro** (proibição de backdating recai sobre estes, não sobre igualar a data do fato à da digitação).
3. Vedado **refazer/reprocessar** lançamentos após o fato — correção somente por novo registro.
4. Proibida a **exclusão de registros consolidados**; o histórico é preservado integralmente.
5. **Bloqueio de novos registros** em período contábil já encerrado.
6. O sistema **não oferece funcionalidade** que altere a essência de registro consolidado (reforço das regras 3 e 4); e as **versões do sistema são identificadas e auditáveis** (Decreto 10.540/2020, art. 9º).
7. Vedada a criação de **usuários genéricos**; toda ação é atribuível a um CPF.
8. Vedada a **manipulação direta da base** fora das rotinas do sistema. As travas críticas (não-negatividade de saldo, unicidade da numeração sequencial, bloqueio físico de escrita e revogação de UPDATE/DELETE em linha consolidada / período encerrado) são reforçadas também no **nível do banco** (constraints), não só na aplicação — a invariante transacional de partidas dobradas soma(D)=soma(C) permanece na aplicação. O acesso privilegiado é nominal e suas ações são gravadas no **store de auditoria segregado** (não na mesma base). Controles de PAM (just-in-time com aprovação, credenciais efêmeras, dual control, gravação de sessão, revisão por controle interno) escalam por porte do ente.
9. **Segregação de funções imposta preventivamente:** matriz de papéis mutuamente exclusivos (quem lança não autoriza; quem autoriza não paga; quem administra acesso não opera financeiro); o motor de RBAC rejeita a atribuição de combinações conflitantes e impede a auto-aprovação (o ordenador/autorizador não pode ser o autor do lançamento). Relatório de exceção de acúmulo de papéis e recertificação periódica.

> **Nota:** a governança de alteração de código-fonte (SDLC versionado e auditável do fornecedor) é um controle organizacional do fornecedor, não uma trava executável pelo sistema, e por isso não figura entre as regras impostas pelo sistema.

---

[← Fluxos do sistema](./04-fluxos.md) · [Índice](./README.md) · [Rastreabilidade →](./06-rastreabilidade.md)
