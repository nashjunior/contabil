-- Corrige RAZ-263: todo POST /execucao/empenhos falha com HTTP 500 desde a
-- RAZ-243 (V14). `VerificarDisponibilidadeArt42` (via `PostgresJanelaMandatoPort`)
-- lê `mandato_inicio`/`mandato_fim` de `ente` com um `select ... from ente where
-- id = ?` direto, na MESMA conexão `app_role` da transação de negócio — mas
-- `ente` nunca teve grant nenhum para `app_role` (RAZ-17, V1: um `select` aqui
-- vazaria o catálogo inteiro de entes — nome/cnpj/esfera de todo tenant — pois
-- `ente` é a raiz multi-tenant, sem `ente_id` próprio para a RLS filtrar).
--
-- Fix preserva a intenção original da RAZ-17 (sem grant direto na tabela): função
-- SECURITY DEFINER, mesmo padrão de `proximo_numero_seq()` (V1) — deriva o ente
-- SEMPRE de `current_setting('app.ente_id')` (a mesma variável de sessão que a
-- RLS usa, setada por `TenantContextUseCasesConfiguration` antes de qualquer
-- `executar(..)` da application), nunca de um argumento passado pelo chamador:
-- se aceitasse um `p_ente_id`, um app_login logado como ente A poderia ler o
-- mandato (e a existência) do ente B só passando o UUID de B — o mesmo risco que
-- já motivou `proximo_numero_seq()` a não aceitar `p_ente_id`.

create function mandato_vigente_do_ente() returns table(mandato_inicio date, mandato_fim date)
language sql
security definer
set search_path = pg_catalog, public
as $$
  select e.mandato_inicio, e.mandato_fim
    from ente e
   where e.id = current_setting('app.ente_id', true)::uuid;
$$;

revoke all on function mandato_vigente_do_ente() from public;
grant execute on function mandato_vigente_do_ente() to app_role;
