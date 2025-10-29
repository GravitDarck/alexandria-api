-- V2__rls_enable_all.sql
-- Habilita RLS (quando as tabelas existirem) e ajusta privilégios de MVs, de forma tolerante.

set search_path to public;

------------------------------------------------------------
-- 0) Função de refresh das materialized views (segura)
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
    where schemaname = 'public'
      and matviewname in ('mv_dash_vendas_dia','mv_top_livros','mv_estoque_baixo','mv_ticket_medio')
  loop
    execute format('refresh materialized view %I.%I', r.schemaname, r.matviewname);
  end loop;
end
$$;

-- Concede EXECUTE/USAGE apenas se a role existir (útil fora do Supabase)
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    execute 'grant execute on function refresh_dashboards_concurrently() to authenticated';
    execute 'grant usage on schema public to authenticated';
  end if;
end$$;

-- Concede SELECT nas MVs somente se elas existirem (e a role também)
do $$
declare
  r record;
begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    for r in
      select schemaname, matviewname
      from pg_matviews
      where schemaname = 'public'
        and matviewname in ('mv_dash_vendas_dia','mv_top_livros','mv_estoque_baixo','mv_ticket_medio')
    loop
      execute format('grant select on table %I.%I to authenticated', r.schemaname, r.matviewname);
    end loop;
  end if;
end
$$ language plpgsql;

------------------------------------------------------------
-- 1) RLS + políticas para um conjunto de tabelas esperadas
--    Só aplica se a tabela existir (evita 42P01).
------------------------------------------------------------
do $$
declare
  t text;
  tabs text[] := array[
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
  role_exists boolean;
begin
  select exists (select 1 from pg_roles where rolname='authenticated') into role_exists;

  foreach t in array tabs loop
    -- só segue se a tabela existir
    if exists (
      select 1
      from information_schema.tables
      where table_schema='public' and table_name=t
    ) then
      -- habilita RLS
      execute format('alter table public.%I enable row level security', t);

      -- cria políticas padrão (nomes únicos por tabela)
      if role_exists then
        execute format('create policy %I on public.%I for select to authenticated using (true)', 'alexapp_select_'||t, t);
        execute format('create policy %I on public.%I for insert to authenticated with check (true)', 'alexapp_insert_'||t, t);
        execute format('create policy %I on public.%I for update to authenticated using (true) with check (true)', 'alexapp_update_'||t, t);
        execute format('create policy %I on public.%I for delete to authenticated using (true)', 'alexapp_delete_'||t, t);
      else
        -- Se não houver role 'authenticated', ainda criamos as políticas sem TO ... (qualquer role herdará via owner)
        execute format('create policy %I on public.%I for select using (true)', 'alexapp_select_'||t, t);
        execute format('create policy %I on public.%I for insert with check (true)', 'alexapp_insert_'||t, t);
        execute format('create policy %I on public.%I for update using (true) with check (true)', 'alexapp_update_'||t, t);
        execute format('create policy %I on public.%I for delete using (true)', 'alexapp_delete_'||t, t);
      end if;

    else
      raise notice 'V2 (RLS): Tabela % não encontrada, ignorando por enquanto.', t;
    end if;
  end loop;
end
$$ language plpgsql;

-- FIM
