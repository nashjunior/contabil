-- V11 — corrige o guardrail ck_auditoria_evento_sem_cpf_claro (RAZ-172).
--
-- O regex de V2 casava QUALQUER corrida de ~11 digitos (com separadores opcionais)
-- em qualquer ponto de ator/recurso/detalhes. UUIDs legitimos que a propria trilha
-- grava (dotacaoId, fatoContabilId, id do empenho no 'recurso') contem corridas de
-- digitos que o casam por acidente (~0,75% dos UUIDs aleatorios — p.ex. o ultimo
-- segmento pode ser 11 digitos + 1 hex, '...-38029583254f'), rejeitando de forma
-- espuria ~2,3% dos registros de empenho com HTTP 500 e quebrando o append da
-- cadeia de auditoria. So aparecia com Postgres real (Testcontainers): os
-- EmpenhoPorIdHttpIntegrationTest falhavam; sem Docker o teste nunca rodava.
--
-- A INTENCAO — nenhum CPF em claro (PII) na trilha — e preservada. Como a unica
-- fonte de falso-positivo sao os UUIDs (hex 8-4-4-4-12), removemos os tokens com
-- forma de UUID ANTES de procurar o padrao de CPF: um CPF real (cru 12345678901
-- ou formatado 123.456.789-01) nao tem forma de UUID, sobrevive ao strip e continua
-- sendo pego; qualquer corrida de digitos que estava dentro de um UUID some.
-- Substituimos por espaco (nao por vazio) para nao colar digitos de campos vizinhos.

alter table auditoria_evento
  drop constraint ck_auditoria_evento_sem_cpf_claro;

alter table auditoria_evento
  add constraint ck_auditoria_evento_sem_cpf_claro check (
    not (
      regexp_replace(ator,          '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}', ' ', 'g') ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
      or regexp_replace(recurso,    '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}', ' ', 'g') ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
      or regexp_replace(detalhes::text, '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}', ' ', 'g') ~ '[0-9]{3}[.]?[0-9]{3}[.]?[0-9]{3}-?[0-9]{2}'
    )
  );
