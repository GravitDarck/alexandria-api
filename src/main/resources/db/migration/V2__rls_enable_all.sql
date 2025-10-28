-- V2__rls_enable_all.sql
-- Habilita Row Level Security (RLS) em TODAS as tabelas e cria políticas completas
-- para a role 'authenticated' no Supabase.
-- Também concede SELECT nas MVs e ajusta função de refresh para SECURITY DEFINER.

set search_path to public;

------------------------------------------------------------
-- 0) Funções/MVs: grants úteis para uso via cliente Supabase
------------------------------------------------------------

-- A função já existe desde a V1; aqui trocamos para SECURITY DEFINER
-- para permitir que usuários 'authenticated' executem o refresh
-- sem necessitar de permissões diretas sobre as tabelas base.
create or replace function refresh_dashboards_concurrently()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  refresh materialized view concurrently mv_dash_vendas_dia;
  refresh materialized view concurrently mv_top_livros;
  refresh materialized view concurrently mv_estoque_baixo;
  refresh materialized view concurrently mv_ticket_medio;
end
$$;

-- Conceder EXECUTE da função aos usuários autenticados
grant execute on function refresh_dashboards_concurrently() to authenticated;

-- MVs não participam de RLS; conceder SELECT explícito
grant select on materialized view mv_dash_vendas_dia    to authenticated;
grant select on materialized view mv_top_livros         to authenticated;
grant select on materialized view mv_estoque_baixo      to authenticated;
grant select on materialized view mv_ticket_medio       to authenticated;

------------------------------------------------------------
-- 1) LISTA DE TABELAS (para referência)
-- usuarios, perfis, permissoes, perfis_permissoes, usuarios_perfis,
-- sessoes, logs_login,
-- departamentos, cargos, funcionarios,
-- clientes, enderecos_cliente, contatos_cliente, categorias, preferencias_cliente,
-- editoras, autores, livros, livros_autores, tabelas_preco, precos, imagens_livros,
-- locais_estoque, estoques, movimentacoes_estoque, reservas_estoque,
-- inventarios, itens_inventario,
-- vendas, itens_venda, formas_pagamento, pagamentos_venda, cupons_desconto, fretes,
-- motivos_devolucao, creditos_cliente, devolucoes_clientes, itens_devolucao_cliente, devolucoes_fornecedores
------------------------------------------------------------

------------------------------------------------------------
-- 2) Habilitar RLS + Políticas por tabela (full access a 'authenticated')
-- Padrão:
--   SELECT: using (true)
--   INSERT: with check (true)
--   UPDATE: using (true) with check (true)
--   DELETE: using (true)
------------------------------------------------------------

-- Helper macro mental (não é SQL de fato): aplique o bloco abaixo para cada tabela
-- alter table <t> enable row level security;
-- create policy alexapp_select_<t> on <t> for select to authenticated using (true);
-- create policy alexapp_insert_<t> on <t> for insert to authenticated with check (true);
-- create policy alexapp_update_<t> on <t> for update to authenticated using (true) with check (true);
-- create policy alexapp_delete_<t> on <t> for delete to authenticated using (true);

-- 2.1 Login / RBAC / Sessões / Auditoria
alter table usuarios enable row level security;
create policy alexapp_select_usuarios on usuarios for select to authenticated using (true);
create policy alexapp_insert_usuarios on usuarios for insert to authenticated with check (true);
create policy alexapp_update_usuarios on usuarios for update to authenticated using (true) with check (true);
create policy alexapp_delete_usuarios on usuarios for delete to authenticated using (true);

alter table perfis enable row level security;
create policy alexapp_select_perfis on perfis for select to authenticated using (true);
create policy alexapp_insert_perfis on perfis for insert to authenticated with check (true);
create policy alexapp_update_perfis on perfis for update to authenticated using (true) with check (true);
create policy alexapp_delete_perfis on perfis for delete to authenticated using (true);

alter table permissoes enable row level security;
create policy alexapp_select_permissoes on permissoes for select to authenticated using (true);
create policy alexapp_insert_permissoes on permissoes for insert to authenticated with check (true);
create policy alexapp_update_permissoes on permissoes for update to authenticated using (true) with check (true);
create policy alexapp_delete_permissoes on permissoes for delete to authenticated using (true);

alter table perfis_permissoes enable row level security;
create policy alexapp_select_perfis_permissoes on perfis_permissoes for select to authenticated using (true);
create policy alexapp_insert_perfis_permissoes on perfis_permissoes for insert to authenticated with check (true);
create policy alexapp_update_perfis_permissoes on perfis_permissoes for update to authenticated using (true) with check (true);
create policy alexapp_delete_perfis_permissoes on perfis_permissoes for delete to authenticated using (true);

alter table usuarios_perfis enable row level security;
create policy alexapp_select_usuarios_perfis on usuarios_perfis for select to authenticated using (true);
create policy alexapp_insert_usuarios_perfis on usuarios_perfis for insert to authenticated with check (true);
create policy alexapp_update_usuarios_perfis on usuarios_perfis for update to authenticated using (true) with check (true);
create policy alexapp_delete_usuarios_perfis on usuarios_perfis for delete to authenticated using (true);

alter table sessoes enable row level security;
create policy alexapp_select_sessoes on sessoes for select to authenticated using (true);
create policy alexapp_insert_sessoes on sessoes for insert to authenticated with check (true);
create policy alexapp_update_sessoes on sessoes for update to authenticated using (true) with check (true);
create policy alexapp_delete_sessoes on sessoes for delete to authenticated using (true);

alter table logs_login enable row level security;
create policy alexapp_select_logs_login on logs_login for select to authenticated using (true);
create policy alexapp_insert_logs_login on logs_login for insert to authenticated with check (true);
create policy alexapp_update_logs_login on logs_login for update to authenticated using (true) with check (true);
create policy alexapp_delete_logs_login on logs_login for delete to authenticated using (true);

-- 2.2 RH
alter table departamentos enable row level security;
create policy alexapp_select_departamentos on departamentos for select to authenticated using (true);
create policy alexapp_insert_departamentos on departamentos for insert to authenticated with check (true);
create policy alexapp_update_departamentos on departamentos for update to authenticated using (true) with check (true);
create policy alexapp_delete_departamentos on departamentos for delete to authenticated using (true);

alter table cargos enable row level security;
create policy alexapp_select_cargos on cargos for select to authenticated using (true);
create policy alexapp_insert_cargos on cargos for insert to authenticated with check (true);
create policy alexapp_update_cargos on cargos for update to authenticated using (true) with check (true);
create policy alexapp_delete_cargos on cargos for delete to authenticated using (true);

alter table funcionarios enable row level security;
create policy alexapp_select_funcionarios on funcionarios for select to authenticated using (true);
create policy alexapp_insert_funcionarios on funcionarios for insert to authenticated with check (true);
create policy alexapp_update_funcionarios on funcionarios for update to authenticated using (true) with check (true);
create policy alexapp_delete_funcionarios on funcionarios for delete to authenticated using (true);

-- 2.3 Clientes
alter table clientes enable row level security;
create policy alexapp_select_clientes on clientes for select to authenticated using (true);
create policy alexapp_insert_clientes on clientes for insert to authenticated with check (true);
create policy alexapp_update_clientes on clientes for update to authenticated using (true) with check (true);
create policy alexapp_delete_clientes on clientes for delete to authenticated using (true);

alter table enderecos_cliente enable row level security;
create policy alexapp_select_enderecos_cliente on enderecos_cliente for select to authenticated using (true);
create policy alexapp_insert_enderecos_cliente on enderecos_cliente for insert to authenticated with check (true);
create policy alexapp_update_enderecos_cliente on enderecos_cliente for update to authenticated using (true) with check (true);
create policy alexapp_delete_enderecos_cliente on enderecos_cliente for delete to authenticated using (true);

alter table contatos_cliente enable row level security;
create policy alexapp_select_contatos_cliente on contatos_cliente for select to authenticated using (true);
create policy alexapp_insert_contatos_cliente on contatos_cliente for insert to authenticated with check (true);
create policy alexapp_update_contatos_cliente on contatos_cliente for update to authenticated using (true) with check (true);
create policy alexapp_delete_contatos_cliente on contatos_cliente for delete to authenticated using (true);

alter table categorias enable row level security;
create policy alexapp_select_categorias on categorias for select to authenticated using (true);
create policy alexapp_insert_categorias on categorias for insert to authenticated with check (true);
create policy alexapp_update_categorias on categorias for update to authenticated using (true) with check (true);
create policy alexapp_delete_categorias on categorias for delete to authenticated using (true);

alter table preferencias_cliente enable row level security;
create policy alexapp_select_preferencias_cliente on preferencias_cliente for select to authenticated using (true);
create policy alexapp_insert_preferencias_cliente on preferencias_cliente for insert to authenticated with check (true);
create policy alexapp_update_preferencias_cliente on preferencias_cliente for update to authenticated using (true) with check (true);
create policy alexapp_delete_preferencias_cliente on preferencias_cliente for delete to authenticated using (true);

-- 2.4 Catálogo
alter table editoras enable row level security;
create policy alexapp_select_editoras on editoras for select to authenticated using (true);
create policy alexapp_insert_editoras on editoras for insert to authenticated with check (true);
create policy alexapp_update_editoras on editoras for update to authenticated using (true) with check (true);
create policy alexapp_delete_editoras on editoras for delete to authenticated using (true);

alter table autores enable row level security;
create policy alexapp_select_autores on autores for select to authenticated using (true);
create policy alexapp_insert_autores on autores for insert to authenticated with check (true);
create policy alexapp_update_autores on autores for update to authenticated using (true) with check (true);
create policy alexapp_delete_autores on autores for delete to authenticated using (true);

alter table livros enable row level security;
create policy alexapp_select_livros on livros for select to authenticated using (true);
create policy alexapp_insert_livros on livros for insert to authenticated with check (true);
create policy alexapp_update_livros on livros for update to authenticated using (true) with check (true);
create policy alexapp_delete_livros on livros for delete to authenticated using (true);

alter table livros_autores enable row level security;
create policy alexapp_select_livros_autores on livros_autores for select to authenticated using (true);
create policy alexapp_insert_livros_autores on livros_autores for insert to authenticated with check (true);
create policy alexapp_update_livros_autores on livros_autores for update to authenticated using (true) with check (true);
create policy alexapp_delete_livros_autores on livros_autores for delete to authenticated using (true);

alter table tabelas_preco enable row level security;
create policy alexapp_select_tabelas_preco on tabelas_preco for select to authenticated using (true);
create policy alexapp_insert_tabelas_preco on tabelas_preco for insert to authenticated with check (true);
create policy alexapp_update_tabelas_preco on tabelas_preco for update to authenticated using (true) with check (true);
create policy alexapp_delete_tabelas_preco on tabelas_preco for delete to authenticated using (true);

alter table precos enable row level security;
create policy alexapp_select_precos on precos for select to authenticated using (true);
create policy alexapp_insert_precos on precos for insert to authenticated with check (true);
create policy alexapp_update_precos on precos for update to authenticated using (true) with check (true);
create policy alexapp_delete_precos on precos for delete to authenticated using (true);

alter table imagens_livros enable row level security;
create policy alexapp_select_imagens_livros on imagens_livros for select to authenticated using (true);
create policy alexapp_insert_imagens_livros on imagens_livros for insert to authenticated with check (true);
create policy alexapp_update_imagens_livros on imagens_livros for update to authenticated using (true) with check (true);
create policy alexapp_delete_imagens_livros on imagens_livros for delete to authenticated using (true);

-- 2.5 Estoque
alter table locais_estoque enable row level security;
create policy alexapp_select_locais_estoque on locais_estoque for select to authenticated using (true);
create policy alexapp_insert_locais_estoque on locais_estoque for insert to authenticated with check (true);
create policy alexapp_update_locais_estoque on locais_estoque for update to authenticated using (true) with check (true);
create policy alexapp_delete_locais_estoque on locais_estoque for delete to authenticated using (true);

alter table estoques enable row level security;
create policy alexapp_select_estoques on estoques for select to authenticated using (true);
create policy alexapp_insert_estoques on estoques for insert to authenticated with check (true);
create policy alexapp_update_estoques on estoques for update to authenticated using (true) with check (true);
create policy alexapp_delete_estoques on estoques for delete to authenticated using (true);

alter table movimentacoes_estoque enable row level security;
create policy alexapp_select_movimentacoes_estoque on movimentacoes_estoque for select to authenticated using (true);
create policy alexapp_insert_movimentacoes_estoque on movimentacoes_estoque for insert to authenticated with check (true);
create policy alexapp_update_movimentacoes_estoque on movimentacoes_estoque for update to authenticated using (true) with check (true);
create policy alexapp_delete_movimentacoes_estoque on movimentacoes_estoque for delete to authenticated using (true);

alter table reservas_estoque enable row level security;
create policy alexapp_select_reservas_estoque on reservas_estoque for select to authenticated using (true);
create policy alexapp_insert_reservas_estoque on reservas_estoque for insert to authenticated with check (true);
create policy alexapp_update_reservas_estoque on reservas_estoque for update to authenticated using (true) with check (true);
create policy alexapp_delete_reservas_estoque on reservas_estoque for delete to authenticated using (true);

alter table inventarios enable row level security;
create policy alexapp_select_inventarios on inventarios for select to authenticated using (true);
create policy alexapp_insert_inventarios on inventarios for insert to authenticated with check (true);
create policy alexapp_update_inventarios on inventarios for update to authenticated using (true) with check (true);
create policy alexapp_delete_inventarios on inventarios for delete to authenticated using (true);

alter table itens_inventario enable row level security;
create policy alexapp_select_itens_inventario on itens_inventario for select to authenticated using (true);
create policy alexapp_insert_itens_inventario on itens_inventario for insert to authenticated with check (true);
create policy alexapp_update_itens_inventario on itens_inventario for update to authenticated using (true) with check (true);
create policy alexapp_delete_itens_inventario on itens_inventario for delete to authenticated using (true);

-- 2.6 Vendas / PDV
alter table vendas enable row level security;
create policy alexapp_select_vendas on vendas for select to authenticated using (true);
create policy alexapp_insert_vendas on vendas for insert to authenticated with check (true);
create policy alexapp_update_vendas on vendas for update to authenticated using (true) with check (true);
create policy alexapp_delete_vendas on vendas for delete to authenticated using (true);

alter table itens_venda enable row level security;
create policy alexapp_select_itens_venda on itens_venda for select to authenticated using (true);
create policy alexapp_insert_itens_venda on itens_venda for insert to authenticated with check (true);
create policy alexapp_update_itens_venda on itens_venda for update to authenticated using (true) with check (true);
create policy alexapp_delete_itens_venda on itens_venda for delete to authenticated using (true);

alter table formas_pagamento enable row level security;
create policy alexapp_select_formas_pagamento on formas_pagamento for select to authenticated using (true);
create policy alexapp_insert_formas_pagamento on formas_pagamento for insert to authenticated with check (true);
create policy alexapp_update_formas_pagamento on formas_pagamento for update to authenticated using (true) with check (true);
create policy alexapp_delete_formas_pagamento on formas_pagamento for delete to authenticated using (true);

alter table pagamentos_venda enable row level security;
create policy alexapp_select_pagamentos_venda on pagamentos_venda for select to authenticated using (true);
create policy alexapp_insert_pagamentos_venda on pagamentos_venda for insert to authenticated with check (true);
create policy alexapp_update_pagamentos_venda on pagamentos_venda for update to authenticated using (true) with check (true);
create policy alexapp_delete_pagamentos_venda on pagamentos_venda for delete to authenticated using (true);

alter table cupons_desconto enable row level security;
create policy alexapp_select_cupons_desconto on cupons_desconto for select to authenticated using (true);
create policy alexapp_insert_cupons_desconto on cupons_desconto for insert to authenticated with check (true);
create policy alexapp_update_cupons_desconto on cupons_desconto for update to authenticated using (true) with check (true);
create policy alexapp_delete_cupons_desconto on cupons_desconto for delete to authenticated using (true);

alter table fretes enable row level security;
create policy alexapp_select_fretes on fretes for select to authenticated using (true);
create policy alexapp_insert_fretes on fretes for insert to authenticated with check (true);
create policy alexapp_update_fretes on fretes for update to authenticated using (true) with check (true);
create policy alexapp_delete_fretes on fretes for delete to authenticated using (true);

-- 2.7 Devoluções & Créditos
alter table motivos_devolucao enable row level security;
create policy alexapp_select_motivos_devolucao on motivos_devolucao for select to authenticated using (true);
create policy alexapp_insert_motivos_devolucao on motivos_devolucao for insert to authenticated with check (true);
create policy alexapp_update_motivos_devolucao on motivos_devolucao for update to authenticated using (true) with check (true);
create policy alexapp_delete_motivos_devolucao on motivos_devolucao for delete to authenticated using (true);

alter table creditos_cliente enable row level security;
create policy alexapp_select_creditos_cliente on creditos_cliente for select to authenticated using (true);
create policy alexapp_insert_creditos_cliente on creditos_cliente for insert to authenticated with check (true);
create policy alexapp_update_creditos_cliente on creditos_cliente for update to authenticated using (true) with check (true);
create policy alexapp_delete_creditos_cliente on creditos_cliente for delete to authenticated using (true);

alter table devolucoes_clientes enable row level security;
create policy alexapp_select_devolucoes_clientes on devolucoes_clientes for select to authenticated using (true);
create policy alexapp_insert_devolucoes_clientes on devolucoes_clientes for insert to authenticated with check (true);
create policy alexapp_update_devolucoes_clientes on devolucoes_clientes for update to authenticated using (true) with check (true);
create policy alexapp_delete_devolucoes_clientes on devolucoes_clientes for delete to authenticated using (true);

alter table itens_devolucao_cliente enable row level security;
create policy alexapp_select_itens_devolucao_cliente on itens_devolucao_cliente for select to authenticated using (true);
create policy alexapp_insert_itens_devolucao_cliente on itens_devolucao_cliente for insert to authenticated with check (true);
create policy alexapp_update_itens_devolucao_cliente on itens_devolucao_cliente for update to authenticated using (true) with check (true);
create policy alexapp_delete_itens_devolucao_cliente on itens_devolucao_cliente for delete to authenticated using (true);

alter table devolucoes_fornecedores enable row level security;
create policy alexapp_select_devolucoes_fornecedores on devolucoes_fornecedores for select to authenticated using (true);
create policy alexapp_insert_devolucoes_fornecedores on devolucoes_fornecedores for insert to authenticated with check (true);
create policy alexapp_update_devolucoes_fornecedores on devolucoes_fornecedores for update to authenticated using (true) with check (true);
create policy alexapp_delete_devolucoes_fornecedores on devolucoes_fornecedores for delete to authenticated using (true);


-- FIM
