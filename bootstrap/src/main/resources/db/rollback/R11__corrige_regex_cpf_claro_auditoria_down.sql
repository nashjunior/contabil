-- Rollback de V11 — restaura o regex original (amplo) do guardrail
-- ck_auditoria_evento_sem_cpf_claro (paridade V<->R, RAZ-72). Apenas ambiente
-- efemero/CI. Na ordem reversa roda ANTES de R2 (que dropa a tabela inteira),
-- entao a tabela ainda existe aqui.

alter table auditoria_evento
  drop constraint if exists ck_auditoria_evento_sem_cpf_claro;

alter table auditoria_evento
  add constraint ck_auditoria_evento_sem_cpf_claro check (
    not (
      ator ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
      or recurso ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
      or detalhes::text ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
    )
  );
