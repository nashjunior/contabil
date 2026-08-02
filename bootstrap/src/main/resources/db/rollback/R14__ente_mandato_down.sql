-- Rollback manual de V14__ente_mandato.sql — RAZ-243.
-- Ambiente efêmero/CI apenas: remove a janela de mandato do ente.

alter table ente
  drop constraint if exists ck_ente_mandato_datas;

alter table ente
  drop column if exists mandato_inicio,
  drop column if exists mandato_fim;
