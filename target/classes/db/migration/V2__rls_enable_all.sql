-- V2__rls_enable_all.sql
-- Habilita RLS/políticas apenas nas tabelas que EXISTEM, procurando o schema certo.
-- Também trata materialized views de forma segura.

------------------------------------------------------------
-- 0) Função de refresh das materialized views (sem CONCURRENTLY)
------------------------------------------------------------
create or replace function refresh_dashboards_concurrently()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
begin
  for r in
    select schemaname, matviewname
    from pg_matviews
    where schemaname not in ('pg_catalog','information_schema')
      and lower(matviewname) in (
        'mv_dash_vendas_dia','mv_top_livros','mv_estoque_baixo','mv_ticket_medio'
      )
  loop
    execute format('refresh materialized view %I.%I', r.schemaname, r.matviewname);
  end loop;
end
$$;

do $$
begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    begin
      execute 'grant execute on function refresh_dashboards_concurrently() to authenticated';
    exception when duplicate_function then null;
    end;
  end if;
end$$;

-- Concede SELECT nas MVs apenas se existirem (e garante USAGE no schema)
do $$
declare
  r record;
begin
  if exists (select 1 from pg_roles where rolname='authenticated') then
    for r in
      select schemaname, matviewname
      from pg_matviews
      where schemaname not in ('pg_catalog','information_schema')
        and lower(matviewname) in ('mv_dash_vendas_dia','mv_top_livros','mv_estoque_baixo','mv_ticket_medio')
    loop
      execute format('grant usage on schema %I to authenticated', r.schemaname);
      execute format('grant select on table %I.%I to authenticated', r.schemaname, r.matviewname);
    end loop;
  end if;
end
$$ language plpgsql;

------------------------------------------------------------
-- 1) RLS + políticas em tabelas alvo (descobrindo o schema e o nome real)
------------------------------------------------------------
do $$
declare
  wanted text[] := array[
    -- Login / RBAC / Sessões / Auditoria
    'usuarios','perfis','permissoes','perfis_permissoes','usuarios_perfis','sessoes','logs_login',
    -- RH
    'departamentos','cargos','funcionarios',
    -- Clientes
    'clientes','enderecos_cliente','contatos_cliente','categorias','preferencias_cliente',
    -- Catálogo
    'editoras','autores','livros','livros_autores','tabelas_preco','precos','imagens_livros',
    -- Estoque
    'locais_estoque','estoques','movimentacoes_estoque','reservas_estoque','inventarios','itens_inventario',
    -- Vendas / PDV
    'vendas','itens_venda','formas_pagamento','pagamentos_venda','cupons_desconto','fretes',
    -- Devoluções & Créditos
    'motivos_devolucao','creditos_cliente','devolucoes_clientes','itens_devolucao_cliente','devolucoes_fornecedores'
  ];
  t text;
  s text;        -- schema encontrado
  real text;     -- nome real da tabela (caso tenha sido criada com aspas/caixa)
  has_auth boolean;
  polname text;
begin
  select exists (select 1 from pg_roles where rolname='authenticated') into has_auth;

  foreach t in array wanted loop
    -- Descobre schema + nome exato (case sensitive) priorizando 'public'
    select table_schema, table_name
    into s, real
    from information_schema.tables
    where table_type='BASE TABLE'
      and lower(table_name)=lower(t)
      and table_schema not in ('pg_catalog','information_schema')
    order by case when table_schema='public' then 0 else 1 end, table_schema
    limit 1;

    if s is null then
      raise notice 'V2 (RLS): Tabela "%" não encontrada em nenhum schema. Ignorando.', t;
      continue;
    end if;

    -- habilita RLS (safe se já estiver habilitado)
    execute format('alter table %I.%I enable row level security', s, real);

    -- cria políticas se ainda não existirem
    polname := 'alexapp_select_'||real;
    if not exists (select 1 from pg_policies where schemaname=s and tablename=real and policyname=polname) then
      if has_auth then
        execute format('create policy %I on %I.%I for select to authenticated using (true)', polname, s, real);
      else
        execute format('create policy %I on %I.%I for select using (true)', polname, s, real);
      end if;
    end if;

    polname := 'alexapp_insert_'||real;
    if not exists (select 1 from pg_policies where schemaname=s and tablename=real and policyname=polname) then
      if has_auth then
        execute format('create policy %I on %I.%I for insert to authenticated with check (true)', polname, s, real);
      else
        execute format('create policy %I on %I.%I for insert with check (true)', polname, s, real);
      end if;
    end if;

    polname := 'alexapp_update_'||real;
    if not exists (select 1 from pg_policies where schemaname=s and tablename=real and policyname=polname) then
      if has_auth then
        execute format('create policy %I on %I.%I for update to authenticated using (true) with check (true)', polname, s, real);
      else
        execute format('create policy %I on %I.%I for update using (true) with check (true)', polname, s, real);
      end if;
    end if;

    polname := 'alexapp_delete_'||real;
    if not exists (select 1 from pg_policies where schemaname=s and tablename=real and policyname=polname) then
      if has_auth then
        execute format('create policy %I on %I.%I for delete to authenticated using (true)', polname, s, real);
      else
        execute format('create policy %I on %I.%I for delete using (true)', polname, s, real);
      end if;
    end if;

    -- garante USAGE no schema para 'authenticated' se existir
    if has_auth then
      begin
        execute format('grant usage on schema %I to authenticated', s);
      exception when others then null;
      end;
    end if;

  end loop;
end
$$ language plpgsql;

-- FIM
