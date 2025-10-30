package br.com.alexandria.alexandria_api.generic;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class TableRegistry {

  public record TableDef(String name, String pk, Set<String> columns, boolean updatable) {}

  private final Map<String, TableDef> definitions = new HashMap<>();

  public TableRegistry() {
    // Login / RBAC
    add("usuarios", "id", Set.of("id", "username", "email", "senha_hash", "ativo", "ultimo_login", "created_at", "updated_at"), true);
    add("perfis", "id", Set.of("id", "nome", "descricao", "created_at", "updated_at"), true);
    add("permissoes", "id", Set.of("id", "codigo", "descricao", "created_at", "updated_at"), true);
    add("usuarios_perfis", null, Set.of("usuario_id", "perfil_id", "created_at"), true);
    add("perfis_permissoes", null, Set.of("perfil_id", "permissao_id", "created_at"), true);
    add("sessoes", "id", Set.of("id", "usuario_id", "jwt_id", "criado_em", "expira_em", "ip", "user_agent", "revogado"), true);
    add("logs_login", "id", Set.of("id", "usuario_id", "instante", "sucesso", "ip", "user_agent", "mensagem"), false);

    // RH
    add("departamentos", "id", Set.of("id", "nome", "descricao", "created_at", "updated_at"), true);
    add("cargos", "id", Set.of("id", "nome", "descricao", "created_at", "updated_at"), true);
    add("funcionarios", "id", Set.of("id", "nome", "cpf", "email", "telefone", "ativo", "departamento_id", "cargo_id", "usuario_id", "created_at", "updated_at"), true);

    // Clientes
    add("clientes", "id", Set.of("id", "tipo_pessoa", "nome", "apelido", "cpf_cnpj", "email", "telefone", "data_nascimento", "ativo", "created_at", "updated_at"), true);
    add("enderecos_cliente", "id", Set.of("id", "cliente_id", "tipo", "logradouro", "numero", "complemento", "bairro", "cidade", "estado", "cep", "principal", "created_at", "updated_at"), true);
    add("contatos_cliente", "id", Set.of("id", "cliente_id", "tipo", "valor", "created_at", "updated_at"), true);
    add("categorias", "id", Set.of("id", "nome", "slug", "descricao", "created_at", "updated_at"), true);
    add("preferencias_cliente", "id", Set.of("id", "cliente_id", "categoria_id", "created_at"), true);

    // Catalogo
    add("editoras", "id", Set.of("id", "nome", "cnpj", "site", "created_at", "updated_at"), true);
    add("autores", "id", Set.of("id", "nome", "bio", "created_at", "updated_at"), true);
    add("livros", "id", Set.of("id", "isbn_13", "titulo", "subtitulo", "edicao", "ano_publicacao", "paginas", "altura_mm", "largura_mm", "espessura_mm", "peso_g", "idioma", "sinopse", "editora_id", "categoria_id", "ativo", "created_at", "updated_at"), true);
    add("livros_autores", null, Set.of("livro_id", "autor_id", "papel", "ordem"), true);
    add("tabelas_preco", "id", Set.of("id", "nome", "descricao", "prioridade", "ativa", "created_at", "updated_at"), true);
    add("precos", "id", Set.of("id", "livro_id", "tabela_preco_id", "valor", "vigencia", "ativo", "created_at", "updated_at"), true);
    add("imagens_livros", "id", Set.of("id", "livro_id", "url", "tipo", "principal", "created_at", "updated_at"), true);

    // Estoque
    add("locais_estoque", "id", Set.of("id", "nome", "tipo", "created_at", "updated_at"), true);
    add("estoques", "id", Set.of("id", "livro_id", "local_id", "quantidade", "reservado", "minimo", "created_at", "updated_at"), true);
    add("movimentacoes_estoque", "id", Set.of("id", "livro_id", "local_id", "tipo", "quantidade", "motivo", "origem_venda_item_id", "origem_dev_cli_item_id", "origem_dev_forn_id", "criado_em"), true);
    add("reservas_estoque", "id", Set.of("id", "livro_id", "local_id", "venda_id", "quantidade", "status", "expira_em", "created_at", "updated_at"), true);
    add("inventarios", "id", Set.of("id", "local_id", "iniciado_em", "finalizado_em", "status", "observacao", "created_at", "updated_at"), true);
    add("itens_inventario", "id", Set.of("id", "inventario_id", "livro_id", "quantidade_sistema", "quantidade_contada", "created_at", "updated_at"), true);

    // Vendas / PDV
    add("vendas", "id", Set.of("id", "codigo", "data_venda", "cliente_id", "funcionario_id", "local_id", "status", "origem", "subtotal", "desconto_total", "frete_total", "total", "observacao", "created_at", "updated_at"), true);
    add("itens_venda", "id", Set.of("id", "venda_id", "livro_id", "quantidade", "preco_unitario", "desconto_valor", "total", "reserva_id", "created_at", "updated_at"), true);
    add("formas_pagamento", "id", Set.of("id", "codigo", "descricao", "ativo", "created_at", "updated_at"), true);
    add("pagamentos_venda", "id", Set.of("id", "venda_id", "forma_id", "valor", "status", "transacao_ref", "criado_em"), true);
    add("cupons_desconto", "id", Set.of("id", "codigo", "descricao", "tipo", "valor", "ativo", "validade_de", "validade_ate", "minimo_compra", "criado_em"), true);
    add("fretes", "id", Set.of("id", "venda_id", "modalidade", "cep_origem", "cep_destino", "valor", "prazo_dias", "codigo_rastreio", "criado_em"), true);

    // Devolucoes
    add("motivos_devolucao", "id", Set.of("id", "codigo", "descricao", "escopo", "created_at", "updated_at"), true);
    add("creditos_cliente", "id", Set.of("id", "cliente_id", "valor_total", "saldo", "codigo", "origem", "emitido_em", "expira_em"), true);
    add("devolucoes_clientes", "id", Set.of("id", "venda_id", "cliente_id", "data_devolucao", "tipo", "motivo_id", "status", "credito_id", "observacao"), true);
    add("itens_devolucao_cliente", "id", Set.of("id", "devolucao_id", "item_venda_id", "livro_id", "quantidade", "valor_unitario", "motivo_id"), true);
    add("devolucoes_fornecedores", "id", Set.of("id", "editora_id", "livro_id", "quantidade", "data_devolucao", "motivo_id", "status", "observacao"), true);

    // Materialized Views (read-only)
    add("mv_dash_vendas_dia", "dia", Set.of("dia", "qtde_vendas", "faturamento", "ticket_medio"), false);
    add("mv_top_livros", "livro_id", Set.of("livro_id", "qtd_vendida", "receita"), false);
    add("mv_estoque_baixo", null, Set.of("livro_id", "local_id", "disponivel", "minimo"), false);
    add("mv_ticket_medio", "dia", Set.of("dia", "ticket_medio"), false);
  }

  private void add(String table, String pk, Set<String> columns, boolean updatable) {
    definitions.put(table, new TableDef(table, pk, columns, updatable));
  }

  public Optional<TableDef> get(String table) {
    return Optional.ofNullable(definitions.get(table));
  }

  public Collection<TableDef> all() {
    return definitions.values();
  }
}
