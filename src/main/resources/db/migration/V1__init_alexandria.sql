-- V1__init_alexandria.sql
-- Alexandria (Sistema de Gestão de Livraria)
-- PostgreSQL 17 / Supabase
-- Padrões: UUID PK, timestamps, FKs estritas

------------------------------
-- PREPARAÇÃO
------------------------------
create extension if not exists pgcrypto; -- para gen_random_uuid()
create extension if not exists btree_gist;

-- Schema (use 'public' no Supabase por simplicidade)
set search_path to public;

------------------------------
-- TIPOS ENUMERADOS
------------------------------
do $$
begin
  if not exists (select 1 from pg_type where typname = 'tipo_mov_estoque') then
    create type tipo_mov_estoque as enum ('ENTRADA','SAIDA','AJUSTE');
  end if;

  if not exists (select 1 from pg_type where typname = 'status_venda') then
    create type status_venda as enum ('ABERTA','FINALIZADA','CANCELADA','ESTORNADA');
  end if;

  if not exists (select 1 from pg_type where typname = 'status_pagamento') then
    create type status_pagamento as enum ('PENDENTE','CONFIRMADO','FALHOU','ESTORNADO');
  end if;

  if not exists (select 1 from pg_type where typname = 'tipo_devolucao_cliente') then
    create type tipo_devolucao_cliente as enum ('TROCA','ESTORNO','CREDITO');
  end if;

  if not exists (select 1 from pg_type where typname = 'status_devolucao') then
    create type status_devolucao as enum ('ABERTA','FINALIZADA','CANCELADA');
  end if;

  if not exists (select 1 from pg_type where typname = 'tipo_local_estoque') then
    create type tipo_local_estoque as enum ('LOJA','DEPOSITO','VITRINE');
  end if;

  if not exists (select 1 from pg_type where typname = 'tipo_endereco') then
    create type tipo_endereco as enum ('ENTREGA','COBRANCA','OUTRO');
  end if;

  if not exists (select 1 from pg_type where typname = 'tipo_contato') then
    create type tipo_contato as enum ('EMAIL','TELEFONE','OUTRO');
  end if;

  if not exists (select 1 from pg_type where typname = 'papel_autoria') then
    create type papel_autoria as enum ('AUTOR','COAUTOR','ORGANIZADOR','TRADUTOR','ILUSTRADOR');
  end if;

  if not exists (select 1 from pg_type where typname = 'tipo_cupom') then
    create type tipo_cupom as enum ('PERCENTUAL','VALOR');
  end if;

  if not exists (select 1 from pg_type where typname = 'status_reserva') then
    create type status_reserva as enum ('ATIVA','CONSUMIDA','EXPIRADA','CANCELADA');
  end if;
end$$;

------------------------------
-- FUNÇÕES DE AUDITORIA
------------------------------
create or replace function set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end$$;

------------------------------
-- LOGIN / RBAC / AUDITORIA DE SESSÃO
------------------------------
create table if not exists usuarios (
  id              uuid primary key default gen_random_uuid(),
  username        text not null unique,
  email           text not null unique,
  senha_hash      text not null,
  ativo           boolean not null default true,
  ultimo_login    timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_usuarios_updated
before update on usuarios
for each row execute function set_updated_at();

create table if not exists perfis (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null unique,
  descricao       text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_perfis_updated
before update on perfis
for each row execute function set_updated_at();

create table if not exists permissoes (
  id              uuid primary key default gen_random_uuid(),
  codigo          text not null unique,
  descricao       text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_permissoes_updated
before update on permissoes
for each row execute function set_updated_at();

create table if not exists perfis_permissoes (
  perfil_id       uuid not null references perfis(id) on delete cascade,
  permissao_id    uuid not null references permissoes(id) on delete cascade,
  created_at      timestamptz not null default now(),
  primary key (perfil_id, permissao_id)
);

create table if not exists usuarios_perfis (
  usuario_id      uuid not null references usuarios(id) on delete cascade,
  perfil_id       uuid not null references perfis(id) on delete cascade,
  created_at      timestamptz not null default now(),
  primary key (usuario_id, perfil_id)
);

create table if not exists sessoes (
  id              uuid primary key default gen_random_uuid(),
  usuario_id      uuid references usuarios(id) on delete set null,
  jwt_id          text not null,
  criado_em       timestamptz not null default now(),
  expira_em       timestamptz not null,
  ip              inet,
  user_agent      text,
  revogado        boolean not null default false
);
create index if not exists idx_sessoes_usuario on sessoes(usuario_id);
create index if not exists idx_sessoes_expira on sessoes(expira_em);

create table if not exists logs_login (
  id              uuid primary key default gen_random_uuid(),
  usuario_id      uuid references usuarios(id) on delete set null,
  instante        timestamptz not null default now(),
  sucesso         boolean not null,
  ip              inet,
  user_agent      text,
  mensagem        text
);
create index if not exists idx_logs_login_usuario on logs_login(usuario_id);
create index if not exists idx_logs_login_instante on logs_login(instante);

------------------------------
-- RH (FUNCIONÁRIOS)
------------------------------
create table if not exists departamentos (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null unique,
  descricao       text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_departamentos_updated
before update on departamentos
for each row execute function set_updated_at();

create table if not exists cargos (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null unique,
  descricao       text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_cargos_updated
before update on cargos
for each row execute function set_updated_at();

create table if not exists funcionarios (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null,
  cpf             text unique,
  email           text unique,
  telefone        text,
  ativo           boolean not null default true,
  departamento_id uuid references departamentos(id),
  cargo_id        uuid references cargos(id),
  usuario_id      uuid unique references usuarios(id) on delete set null,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_funcionarios_dep on funcionarios(departamento_id);
create index if not exists idx_funcionarios_cargo on funcionarios(cargo_id);
create trigger tg_funcionarios_updated
before update on funcionarios
for each row execute function set_updated_at();

------------------------------
-- CLIENTES
------------------------------
create table if not exists clientes (
  id              uuid primary key default gen_random_uuid(),
  tipo_pessoa     text check (tipo_pessoa in ('PF','PJ')) default 'PF',
  nome            text not null,
  apelido         text,
  cpf_cnpj        text unique,
  email           text,
  telefone        text,
  data_nascimento date,
  ativo           boolean not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_clientes_email on clientes(email);
create trigger tg_clientes_updated
before update on clientes
for each row execute function set_updated_at();

create table if not exists enderecos_cliente (
  id              uuid primary key default gen_random_uuid(),
  cliente_id      uuid not null references clientes(id) on delete cascade,
  tipo            tipo_endereco not null default 'ENTREGA',
  logradouro      text not null,
  numero          text,
  complemento     text,
  bairro          text,
  cidade          text,
  estado          text,
  cep             text,
  principal       boolean not null default false,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_enderecos_cliente_cliente on enderecos_cliente(cliente_id);
create trigger tg_enderecos_cliente_updated
before update on enderecos_cliente
for each row execute function set_updated_at();

create table if not exists contatos_cliente (
  id              uuid primary key default gen_random_uuid(),
  cliente_id      uuid not null references clientes(id) on delete cascade,
  tipo            tipo_contato not null default 'EMAIL',
  valor           text not null,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_contatos_cliente_cliente on contatos_cliente(cliente_id);
create trigger tg_contatos_cliente_updated
before update on contatos_cliente
for each row execute function set_updated_at();

create table if not exists categorias (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null unique,
  slug            text unique,
  descricao       text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_categorias_updated
before update on categorias
for each row execute function set_updated_at();

create table if not exists preferencias_cliente (
  id              uuid primary key default gen_random_uuid(),
  cliente_id      uuid not null references clientes(id) on delete cascade,
  categoria_id    uuid not null references categorias(id),
  created_at      timestamptz not null default now(),
  unique (cliente_id, categoria_id)
);

------------------------------
-- CATÁLOGO DE LIVROS
------------------------------
create table if not exists editoras (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null unique,
  cnpj            text unique,
  site            text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_editoras_updated
before update on editoras
for each row execute function set_updated_at();

create table if not exists autores (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null,
  bio             text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_autores_nome on autores(nome);
create trigger tg_autores_updated
before update on autores
for each row execute function set_updated_at();

create table if not exists livros (
  id              uuid primary key default gen_random_uuid(),
  isbn_13         text unique,
  titulo          text not null,
  subtitulo       text,
  edicao          integer,
  ano_publicacao  integer,
  paginas         integer,
  altura_mm       numeric(8,2),
  largura_mm      numeric(8,2),
  espessura_mm    numeric(8,2),
  peso_g          numeric(10,2),
  idioma          text,
  sinopse         text,
  editora_id      uuid references editoras(id),
  categoria_id    uuid references categorias(id),
  ativo           boolean not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_livros_editora on livros(editora_id);
create index if not exists idx_livros_categoria on livros(categoria_id);
create trigger tg_livros_updated
before update on livros
for each row execute function set_updated_at();

create table if not exists livros_autores (
  livro_id        uuid not null references livros(id) on delete cascade,
  autor_id        uuid not null references autores(id) on delete cascade,
  papel           papel_autoria not null default 'AUTOR',
  ordem           integer not null default 1,
  primary key (livro_id, autor_id, papel)
);

create table if not exists tabelas_preco (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null unique,
  descricao       text,
  prioridade      integer not null default 100,
  ativa           boolean not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_tabelas_preco_updated
before update on tabelas_preco
for each row execute function set_updated_at();

-- Preços com histórico usando tsrange + exclusão por livro/tabela
create table if not exists precos (
  id              uuid primary key default gen_random_uuid(),
  livro_id        uuid not null references livros(id) on delete cascade,
  tabela_preco_id uuid not null references tabelas_preco(id) on delete cascade,
  valor           numeric(12,2) not null check (valor >= 0),
  vigencia        tsrange not null,
  ativo           boolean not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  exclude using gist (
    livro_id with =,
    tabela_preco_id with =,
    vigencia with &&
  )
);
create index if not exists idx_precos_livro on precos(livro_id);
create index if not exists idx_precos_tab on precos(tabela_preco_id);
create trigger tg_precos_updated
before update on precos
for each row execute function set_updated_at();

create table if not exists imagens_livros (
  id              uuid primary key default gen_random_uuid(),
  livro_id        uuid not null references livros(id) on delete cascade,
  url             text not null,
  tipo            text, -- ex: 'CAPA','CONTRACAPA','EXTRA'
  principal       boolean not null default false,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_imagens_livros_livro on imagens_livros(livro_id);
create trigger tg_imagens_livros_updated
before update on imagens_livros
for each row execute function set_updated_at();

------------------------------
-- ESTOQUE
------------------------------
create table if not exists locais_estoque (
  id              uuid primary key default gen_random_uuid(),
  nome            text not null unique,
  tipo            tipo_local_estoque not null default 'LOJA',
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_locais_estoque_updated
before update on locais_estoque
for each row execute function set_updated_at();

create table if not exists estoques (
  id              uuid primary key default gen_random_uuid(),
  livro_id        uuid not null references livros(id) on delete cascade,
  local_id        uuid not null references locais_estoque(id) on delete cascade,
  quantidade      integer not null default 0,
  reservado       integer not null default 0,
  minimo          integer not null default 0,
  unique (livro_id, local_id),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  check (quantidade >= 0),
  check (reservado >= 0),
  check (minimo >= 0)
);
create index if not exists idx_estoques_livro on estoques(livro_id);
create index if not exists idx_estoques_local on estoques(local_id);
create trigger tg_estoques_updated
before update on estoques
for each row execute function set_updated_at();

create table if not exists movimentacoes_estoque (
  id              uuid primary key default gen_random_uuid(),
  livro_id        uuid not null references livros(id) on delete restrict,
  local_id        uuid not null references locais_estoque(id) on delete restrict,
  tipo            tipo_mov_estoque not null,
  quantidade      integer not null check (quantidade > 0),
  motivo          text,
  origem_venda_item_id uuid references itens_venda(id),            -- FK adicionado após itens_venda
  origem_dev_cli_item_id uuid references itens_devolucao_cliente(id), -- FK após criação
  origem_dev_forn_id uuid,                                         -- referenciará devolucoes_fornecedores
  criado_em       timestamptz not null default now()
);
create index if not exists idx_mov_estoque_livro on movimentacoes_estoque(livro_id);
create index if not exists idx_mov_estoque_local on movimentacoes_estoque(local_id);
create index if not exists idx_mov_estoque_tipo on movimentacoes_estoque(tipo);

create table if not exists reservas_estoque (
  id              uuid primary key default gen_random_uuid(),
  livro_id        uuid not null references livros(id) on delete restrict,
  local_id        uuid not null references locais_estoque(id) on delete restrict,
  venda_id        uuid, -- preenchido quando associado a uma venda
  quantidade      integer not null check (quantidade > 0),
  status          status_reserva not null default 'ATIVA',
  expira_em       timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_reservas_livro_local on reservas_estoque(livro_id, local_id);
create trigger tg_reservas_estoque_updated
before update on reservas_estoque
for each row execute function set_updated_at();

create table if not exists inventarios (
  id              uuid primary key default gen_random_uuid(),
  local_id        uuid not null references locais_estoque(id) on delete restrict,
  iniciado_em     timestamptz not null default now(),
  finalizado_em   timestamptz,
  status          text not null default 'ABERTO' check (status in ('ABERTO','FINALIZADO','CANCELADO')),
  observacao      text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_inventarios_local on inventarios(local_id);
create trigger tg_inventarios_updated
before update on inventarios
for each row execute function set_updated_at();

create table if not exists itens_inventario (
  id              uuid primary key default gen_random_uuid(),
  inventario_id   uuid not null references inventarios(id) on delete cascade,
  livro_id        uuid not null references livros(id),
  quantidade_sistema integer not null default 0,
  quantidade_contada integer not null default 0,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  unique (inventario_id, livro_id)
);
create index if not exists idx_itens_inventario_inv on itens_inventario(inventario_id);
create trigger tg_itens_inventario_updated
before update on itens_inventario
for each row execute function set_updated_at();

------------------------------
-- VENDAS / PDV
------------------------------
create table if not exists vendas (
  id              uuid primary key default gen_random_uuid(),
  codigo          text unique, -- opcional para etiqueta/PDV
  data_venda      timestamptz not null default now(),
  cliente_id      uuid references clientes(id),
  funcionario_id  uuid references funcionarios(id),
  local_id        uuid references locais_estoque(id), -- loja
  status          status_venda not null default 'ABERTA',
  origem          text check (origem in ('BALCAO','PEDIDO')) default 'BALCAO',
  subtotal        numeric(12,2) not null default 0,
  desconto_total  numeric(12,2) not null default 0,
  frete_total     numeric(12,2) not null default 0,
  total           numeric(12,2) not null default 0,
  observacao      text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_vendas_data on vendas(data_venda);
create index if not exists idx_vendas_cliente on vendas(cliente_id);
create index if not exists idx_vendas_func on vendas(funcionario_id);
create trigger tg_vendas_updated
before update on vendas
for each row execute function set_updated_at();

create table if not exists itens_venda (
  id              uuid primary key default gen_random_uuid(),
  venda_id        uuid not null references vendas(id) on delete cascade,
  livro_id        uuid not null references livros(id),
  quantidade      integer not null check (quantidade > 0),
  preco_unitario  numeric(12,2) not null check (preco_unitario >= 0),
  desconto_valor  numeric(12,2) not null default 0 check (desconto_valor >= 0),
  total           numeric(12,2) not null check (total >= 0),
  reserva_id      uuid references reservas_estoque(id),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index if not exists idx_itens_venda_venda on itens_venda(venda_id);
create index if not exists idx_itens_venda_livro on itens_venda(livro_id);
create trigger tg_itens_venda_updated
before update on itens_venda
for each row execute function set_updated_at();

create table if not exists formas_pagamento (
  id              uuid primary key default gen_random_uuid(),
  codigo          text not null unique, -- ex: PIX, CARTAO, DINHEIRO
  descricao       text,
  ativo           boolean not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_formas_pagamento_updated
before update on formas_pagamento
for each row execute function set_updated_at();

create table if not exists pagamentos_venda (
  id              uuid primary key default gen_random_uuid(),
  venda_id        uuid not null references vendas(id) on delete cascade,
  forma_id        uuid not null references formas_pagamento(id),
  valor           numeric(12,2) not null check (valor >= 0),
  status          status_pagamento not null default 'PENDENTE',
  transacao_ref   text,
  criado_em       timestamptz not null default now()
);
create index if not exists idx_pag_venda_venda on pagamentos_venda(venda_id);

create table if not exists cupons_desconto (
  id              uuid primary key default gen_random_uuid(),
  codigo          text not null unique,
  descricao       text,
  tipo            tipo_cupom not null,
  valor           numeric(12,2) not null check (valor >= 0),
  ativo           boolean not null default true,
  validade_de     timestamptz,
  validade_ate    timestamptz,
  minimo_compra   numeric(12,2) not null default 0,
  criado_em       timestamptz not null default now()
);

create table if not exists fretes (
  id              uuid primary key default gen_random_uuid(),
  venda_id        uuid not null references vendas(id) on delete cascade,
  modalidade      text not null,
  cep_origem      text,
  cep_destino     text,
  valor           numeric(12,2) not null default 0,
  prazo_dias      integer,
  codigo_rastreio text,
  criado_em       timestamptz not null default now()
);
create index if not exists idx_fretes_venda on fretes(venda_id);

------------------------------
-- DEVOLUÇÕES & TROCAS
------------------------------
create table if not exists motivos_devolucao (
  id              uuid primary key default gen_random_uuid(),
  codigo          text not null unique,
  descricao       text not null,
  escopo          text not null check (escopo in ('CLIENTE','FORNECEDOR')),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create trigger tg_motivos_devolucao_updated
before update on motivos_devolucao
for each row execute function set_updated_at();

create table if not exists creditos_cliente (
  id              uuid primary key default gen_random_uuid(),
  cliente_id      uuid not null references clientes(id),
  valor_total     numeric(12,2) not null check (valor_total >= 0),
  saldo           numeric(12,2) not null check (saldo >= 0),
  codigo          text unique,
  origem          text not null check (origem in ('DEVOLUCAO','AJUSTE')),
  emitido_em      timestamptz not null default now(),
  expira_em       timestamptz
);
create index if not exists idx_creditos_cliente_cli on creditos_cliente(cliente_id);

create table if not exists devolucoes_clientes (
  id              uuid primary key default gen_random_uuid(),
  venda_id        uuid references vendas(id),
  cliente_id      uuid references clientes(id),
  data_devolucao  timestamptz not null default now(),
  tipo            tipo_devolucao_cliente not null,
  motivo_id       uuid references motivos_devolucao(id),
  status          status_devolucao not null default 'ABERTA',
  credito_id      uuid references creditos_cliente(id),
  observacao      text
);
create index if not exists idx_dev_cli_venda on devolucoes_clientes(venda_id);

create table if not exists itens_devolucao_cliente (
  id              uuid primary key default gen_random_uuid(),
  devolucao_id    uuid not null references devolucoes_clientes(id) on delete cascade,
  item_venda_id   uuid references itens_venda(id),
  livro_id        uuid not null references livros(id),
  quantidade      integer not null check (quantidade > 0),
  valor_unitario  numeric(12,2) not null check (valor_unitario >= 0),
  motivo_id       uuid references motivos_devolucao(id)
);
create index if not exists idx_itens_dev_cli_dev on itens_devolucao_cliente(devolucao_id);

-- Devolução a fornecedores (usaremos editora como fornecedor)
create table if not exists devolucoes_fornecedores (
  id              uuid primary key default gen_random_uuid(),
  editora_id      uuid not null references editoras(id),
  livro_id        uuid not null references livros(id),
  quantidade      integer not null check (quantidade > 0),
  data_devolucao  timestamptz not null default now(),
  motivo_id       uuid references motivos_devolucao(id),
  status          status_devolucao not null default 'ABERTA',
  observacao      text
);
create index if not exists idx_dev_forn_editora on devolucoes_fornecedores(editora_id);

-- Agora que as tabelas existem, amarrar FKs opcionais em movimentações
alter table movimentacoes_estoque
  add constraint fk_mov_est_origem_dev_forn
  foreign key (origem_dev_forn_id) references devolucoes_fornecedores(id);

------------------------------
-- VIEWS / MATERIALIZED VIEWS (Home/Dashboards)
------------------------------

-- 1) Vendas por dia / ticket médio
create materialized view if not exists mv_dash_vendas_dia as
select
  date_trunc('day', v.data_venda)::date as dia,
  count(*) filter (where v.status = 'FINALIZADA') as qtde_vendas,
  sum(case when v.status = 'FINALIZADA' then v.total else 0 end) as faturamento,
  nullif(sum(case when v.status = 'FINALIZADA' then v.total else 0 end),0) /
  nullif(count(*) filter (where v.status = 'FINALIZADA'),0) as ticket_medio
from vendas v
group by 1;

-- índice único para refresh concurrently
create unique index if not exists ux_mv_dash_vendas_dia on mv_dash_vendas_dia(dia);

-- 2) Top livros últimos 30 dias (por quantidade vendida)
create materialized view if not exists mv_top_livros as
select
  iv.livro_id,
  sum(iv.quantidade) as qtd_vendida,
  sum(iv.total) as receita
from itens_venda iv
join vendas v on v.id = iv.venda_id
where v.status = 'FINALIZADA'
  and v.data_venda >= now() - interval '30 days'
group by iv.livro_id
order by qtd_vendida desc;

create unique index if not exists ux_mv_top_livros on mv_top_livros(livro_id);

-- 3) Estoque baixo (quantidade disponível <= mínimo)
create materialized view if not exists mv_estoque_baixo as
select
  e.livro_id,
  e.local_id,
  greatest(e.quantidade - e.reservado, 0) as disponivel,
  e.minimo
from estoques e
where (e.quantidade - e.reservado) <= e.minimo;

create unique index if not exists ux_mv_estoque_baixo on mv_estoque_baixo(livro_id, local_id);

-- 4) Ticket médio diário (igual à 1, mas separado se quiser consultar rápido)
create materialized view if not exists mv_ticket_medio as
select
  date_trunc('day', v.data_venda)::date as dia,
  nullif(sum(case when v.status = 'FINALIZADA' then v.total else 0 end),0) /
  nullif(count(*) filter (where v.status = 'FINALIZADA'),0) as ticket_medio
from vendas v
group by 1;

create unique index if not exists ux_mv_ticket_medio on mv_ticket_medio(dia);

-- Função utilitária para refresh das MVs
create or replace function refresh_dashboards_concurrently()
returns void language plpgsql as $$
begin
  refresh materialized view concurrently mv_dash_vendas_dia;
  refresh materialized view concurrently mv_top_livros;
  refresh materialized view concurrently mv_estoque_baixo;
  refresh materialized view concurrently mv_ticket_medio;
end$$;

------------------------------
-- RELACIONAMENTOS FALTANTES (após criação de tabelas cruzadas)
------------------------------
-- Linkar reservas à venda só quando existir a venda (FK opcional)
alter table reservas_estoque
  add constraint fk_reservas_venda
  foreign key (venda_id) references vendas(id) on delete set null;

-- Referências de movimentações às linhas existentes (já adicionadas acima para itens/ devoluções)
alter table movimentacoes_estoque
  add constraint fk_mov_est_item_venda
  foreign key (origem_venda_item_id) references itens_venda(id);

alter table movimentacoes_estoque
  add constraint fk_mov_est_item_dev_cli
  foreign key (origem_dev_cli_item_id) references itens_devolucao_cliente(id);

------------------------------
-- ÍNDICES COMPLEMENTARES
------------------------------
create index if not exists idx_vendas_status on vendas(status);
create index if not exists idx_itens_venda_venda_livro on itens_venda(venda_id, livro_id);
create index if not exists idx_precos_vigencia on precos using gist (vigencia);

------------------------------
-- FIM
------------------------------
