-- RAZ-212: piso de observabilidade — correlação API -> outbox -> worker.
--
-- O id de correlação nasce na borda HTTP (X-Correlation-Id/MDC), é persistido
-- junto da intenção de entrega e volta ao MDC quando o worker reclama a
-- mensagem. Linhas legadas usam o próprio id da mensagem como correlação
-- técnica para continuarem rastreáveis sem inventar vínculo histórico.

alter table outbox_mensagem add column correlation_id text default uuid_generate_v4()::text;
update outbox_mensagem
   set correlation_id = id::text
 where correlation_id is null;
alter table outbox_mensagem alter column correlation_id set not null;
alter table outbox_mensagem
  add constraint ck_outbox_mensagem_correlation_id
  check (correlation_id ~ '^[A-Za-z0-9._:-]{1,128}$');

alter table outbox_dlq add column correlation_id text default uuid_generate_v4()::text;
update outbox_dlq d
   set correlation_id = m.correlation_id
  from outbox_mensagem m
 where d.mensagem_id = m.id
   and d.correlation_id is null;
update outbox_dlq
   set correlation_id = mensagem_id::text
 where correlation_id is null;
alter table outbox_dlq alter column correlation_id set not null;
alter table outbox_dlq
  add constraint ck_outbox_dlq_correlation_id
  check (correlation_id ~ '^[A-Za-z0-9._:-]{1,128}$');

alter table execucao_empenho_documento_outbox add column correlation_id text default uuid_generate_v4()::text;
update execucao_empenho_documento_outbox
   set correlation_id = id::text
 where correlation_id is null;
alter table execucao_empenho_documento_outbox alter column correlation_id set not null;
alter table execucao_empenho_documento_outbox
  add constraint ck_execucao_empenho_documento_outbox_correlation_id
  check (correlation_id ~ '^[A-Za-z0-9._:-]{1,128}$');

drop function outbox_entrega_reclamar(int, timestamptz);
create function outbox_entrega_reclamar(p_limite int, p_bloqueado_ate timestamptz)
returns table (
  id uuid,
  ente_id uuid,
  chave text,
  destino text,
  tipo text,
  conteudo text,
  correlation_id text,
  criado_em timestamptz,
  tentativas int
)
language sql
security definer
set search_path = pg_catalog, public
as $$
  with selecionadas as (
    select m.id
      from outbox_mensagem m
     where m.status in ('enfileirado', 'retentando')
       and m.proxima_tentativa_em <= clock_timestamp()
       and (m.bloqueado_ate is null or m.bloqueado_ate <= clock_timestamp())
     order by m.criado_em, m.id
     for update skip locked
     limit least(greatest(p_limite, 1), 1000)
  ),
  atualizadas as (
    update outbox_mensagem m
       set bloqueado_ate = p_bloqueado_ate,
           atualizado_em = clock_timestamp()
      from selecionadas s
     where m.id = s.id
     returning m.id, m.ente_id, m.chave, m.destino, m.tipo, m.conteudo, m.correlation_id, m.criado_em, m.tentativas
  )
  select * from atualizadas;
$$;

drop function outbox_entrega_dlq(uuid, text);
create function outbox_entrega_dlq(p_id uuid, p_erro text)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_mensagem outbox_mensagem%rowtype;
begin
  update outbox_mensagem
     set status = 'falha_permanente',
         tentativas = tentativas + 1,
         bloqueado_ate = null,
         ultimo_erro = left(p_erro, 4000),
         atualizado_em = clock_timestamp()
   where id = p_id
     and status in ('enfileirado', 'retentando')
   returning * into v_mensagem;

  if found then
    insert into outbox_dlq (mensagem_id, ente_id, chave, destino, tipo, conteudo, correlation_id, tentativas, erro)
    values (
      v_mensagem.id,
      v_mensagem.ente_id,
      v_mensagem.chave,
      v_mensagem.destino,
      v_mensagem.tipo,
      v_mensagem.conteudo,
      v_mensagem.correlation_id,
      v_mensagem.tentativas,
      left(p_erro, 4000)
    )
    on conflict (mensagem_id) do update
       set tentativas = excluded.tentativas,
           erro = excluded.erro,
           criado_em = clock_timestamp();
  end if;
end;
$$;

drop function execucao_empenho_documento_outbox_reclamar(int, timestamptz);
create function execucao_empenho_documento_outbox_reclamar(p_limite int, p_bloqueado_ate timestamptz)
returns table (
  id uuid,
  ente_id uuid,
  empenho_id uuid,
  correlation_id text,
  criado_em timestamptz,
  tentativas int
)
language sql
security definer
set search_path = pg_catalog, public
as $$
  with selecionadas as (
    select o.id
      from execucao_empenho_documento_outbox o
     where o.status in ('pendente', 'retentando')
       and o.proxima_tentativa_em <= clock_timestamp()
       and (o.bloqueado_ate is null or o.bloqueado_ate <= clock_timestamp())
     order by o.criado_em, o.id
     for update skip locked
     limit least(greatest(p_limite, 1), 1000)
  ),
  atualizadas as (
    update execucao_empenho_documento_outbox o
       set bloqueado_ate = p_bloqueado_ate,
           atualizado_em = clock_timestamp()
      from selecionadas s
     where o.id = s.id
     returning o.id, o.ente_id, o.empenho_id, o.correlation_id, o.criado_em, o.tentativas
  )
  select * from atualizadas;
$$;

revoke all on function
  outbox_entrega_reclamar(int, timestamptz),
  outbox_entrega_dlq(uuid, text),
  execucao_empenho_documento_outbox_reclamar(int, timestamptz)
from public;

grant execute on function
  outbox_entrega_reclamar(int, timestamptz),
  outbox_entrega_dlq(uuid, text),
  execucao_empenho_documento_outbox_reclamar(int, timestamptz)
to app_role;
