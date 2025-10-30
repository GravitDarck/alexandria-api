package br.com.alexandria.alexandria_api.vendas;

import br.com.alexandria.alexandria_api.estoque.EstoqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VendaService {

  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate njdbc;
  private final EstoqueService estoque;

  // ========== ABRIR ===========================================================

  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String,Object> abrir(Object reqObj){
    var r = (Map<String,Object>) reqObj;
    UUID id = UUID.randomUUID();
    String codigo = "V-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);

    jdbc.update("""
      insert into vendas (id, codigo, data_venda, cliente_id, funcionario_id, local_id, status, origem,
                          subtotal, desconto_total, frete_total, total, observacao, created_at, updated_at)
      values (?, ?, now(), ?, ?, ?, 'ABERTA', ?, 0, 0, 0, 0, ?, now(), now())
      """,
        id,
        codigo,
        (UUID) r.get("clienteId"),
        (UUID) r.get("funcionarioId"),
        (UUID) r.get("localId"),
        (String) r.getOrDefault("origem","PDV"),
        (String) r.getOrDefault("observacao","")
      );

    return getVenda(id);
  }

  // ========== ITENS ===========================================================

  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String,Object> addItem(UUID vendaId, Object reqObj){
    var r = (Map<String,Object>) reqObj;
    UUID livroId = (UUID) r.get("livroId");
    int qtd = ((Number) r.get("quantidade")).intValue();
    BigDecimal preco = toBd(r.get("precoUnit"));
    BigDecimal desc = toBd(r.get("desconto"));

    var venda = getVendaOrThrow(vendaId);
    assertAberta(venda);

    // reserva estoque
    Map<String,Object> reserva = estoque.reservar(new EstoqueService.Reserva(livroId, (UUID)venda.get("local_id"), vendaId, qtd));
    UUID reservaId = (UUID) reserva.get("id");

    UUID itemId = UUID.randomUUID();
    BigDecimal totalItem = preco.multiply(BigDecimal.valueOf(qtd)).subtract(nullSafe(desc));

    jdbc.update("""
      insert into itens_venda (id, venda_id, livro_id, quantidade, preco_unitario, desconto_valor, total, reserva_id, created_at, updated_at)
      values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
      """, itemId, vendaId, livroId, qtd, preco, nullSafe(desc), totalItem, reservaId);

    recalc(vendaId);
    return getItem(itemId);
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String,Object> updateItem(UUID vendaId, UUID itemId, Object reqObj){
    var r = (Map<String,Object>) reqObj;
    var venda = getVendaOrThrow(vendaId);
    assertAberta(venda);

    var old = getItemOrThrow(itemId);

    // libera reserva antiga (se existir)
    if(old.get("reserva_id") != null){
      estoque.liberar((UUID) old.get("reserva_id"));
    }

    // cria nova reserva com a nova quantidade
    UUID livroId = (UUID) r.get("livroId");
    int qtd = ((Number) r.get("quantidade")).intValue();
    BigDecimal preco = toBd(r.get("precoUnit"));
    BigDecimal desc = toBd(r.get("desconto"));

    Map<String,Object> reserva = estoque.reservar(new EstoqueService.Reserva(livroId, (UUID)venda.get("local_id"), vendaId, qtd));
    UUID novaReservaId = (UUID) reserva.get("id");

    BigDecimal totalItem = preco.multiply(BigDecimal.valueOf(qtd)).subtract(nullSafe(desc));

    jdbc.update("""
      update itens_venda set livro_id=?, quantidade=?, preco_unitario=?, desconto_valor=?, total=?, reserva_id=?, updated_at=now()
      where id=?
      """, livroId, qtd, preco, nullSafe(desc), totalItem, novaReservaId, itemId);

    recalc(vendaId);
    return getItem(itemId);
  }

  @Transactional
  public void removeItem(UUID vendaId, UUID itemId){
    var venda = getVendaOrThrow(vendaId);
    assertAberta(venda);

    var old = getItemOrThrow(itemId);
    if(old.get("reserva_id") != null){
      estoque.liberar((UUID) old.get("reserva_id"));
    }
    jdbc.update("delete from itens_venda where id=?", itemId);
    recalc(vendaId);
  }

  // ========== CUPOM / FRETE / PAGAMENTO ======================================

  @Transactional
  public Map<String,Object> aplicarCupom(UUID vendaId, String codigo){
    var venda = getVendaOrThrow(vendaId);
    assertAberta(venda);

    Map<String,Object> cupom;
    try {
      cupom = jdbc.queryForMap("""
        select * from cupons_desconto
        where codigo=? and ativo=true
          and (validade_de is null or validade_de <= current_date)
          and (validade_ate is null or validade_ate >= current_date)
        """, codigo);
    } catch (EmptyResultDataAccessException e){
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Cupom invalido ou expirado");
    }

    BigDecimal subtotal = (BigDecimal) venda.get("subtotal");
    BigDecimal min = (BigDecimal) cupom.getOrDefault("minimo_compra", BigDecimal.ZERO);
    if(subtotal.compareTo(min) < 0)
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Subtotal insuficiente para este cupom");

    String tipo = String.valueOf(cupom.get("tipo")).toUpperCase(Locale.ROOT); // PERCENTUAL | VALOR
    BigDecimal valor = (BigDecimal) cupom.get("valor");
    BigDecimal descontoCupom = tipo.equals("PERCENTUAL")
        ? subtotal.multiply(valor).divide(BigDecimal.valueOf(100))
        : valor;

    // aplica no campo desconto_total (somando ao desconto por item)
    jdbc.update("update vendas set desconto_total = coalesce(desconto_total,0) + ?, updated_at=now() where id=?",
        descontoCupom, vendaId);

    recalc(vendaId); // garante total correto
    return getVenda(vendaId);
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String,Object> definirFrete(UUID vendaId, Object reqObj){
    var r = (Map<String,Object>) reqObj;
    var venda = getVendaOrThrow(vendaId);
    assertAberta(venda);

    // Apaga frete anterior, se existir, e insere o novo
    jdbc.update("delete from fretes where venda_id=?", vendaId);
    jdbc.update("""
      insert into fretes (id, venda_id, modalidade, cep_origem, cep_destino, valor, prazo_dias, codigo_rastreio, criado_em)
      values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, now())
      """, vendaId, r.get("modalidade"), r.get("cepOrigem"), r.get("cepDestino"),
        toBd(r.get("valor")), r.get("prazoDias"), r.get("codigoRastreio"));

    // atualiza frete_total
    jdbc.update("update vendas set frete_total = ?, updated_at=now() where id=?", toBd(r.get("valor")), vendaId);
    recalc(vendaId);
    return getVenda(vendaId);
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String,Object> registrarPagamento(UUID vendaId, Object reqObj){
    var r = (Map<String,Object>) reqObj;
    var venda = getVendaOrThrow(vendaId);

    jdbc.update("""
      insert into pagamentos_venda (id, venda_id, forma_id, valor, status, transacao_ref, criado_em)
      values (gen_random_uuid(), ?, ?, ?, 'APROVADO', ?, now())
      """, vendaId, (UUID) r.get("formaId"), toBd(r.get("valor")), (String) r.get("transacaoRef"));

    // status informativo
    BigDecimal pagos = totalPago(vendaId);
    BigDecimal total = totalVenda(vendaId);
    String novoStatus = pagos.compareTo(total) >= 0 ? "PAGO" : "PAGO_PARCIAL";
    jdbc.update("update vendas set status=?, updated_at=now() where id=?", novoStatus, vendaId);
    return getVenda(vendaId);
  }

  // ========== FINALIZAR / CANCELAR / ESTORNAR =================================

  @Transactional
  public Map<String,Object> finalizar(UUID vendaId){
    var venda = getVendaOrThrow(vendaId);
    assertAbertaOuPago(venda);

    BigDecimal pagos = totalPago(vendaId);
    BigDecimal total = totalVenda(vendaId);
    if(pagos.compareTo(total) < 0)
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Pagamento insuficiente");

    // consome reservas e da baixa no estoque
    List<Map<String,Object>> itens = itens(vendaId);
    for (var it : itens){
      UUID resId = (UUID) it.get("reserva_id");
      if(resId != null){
        estoque.consumir(resId, (UUID) it.get("id"));
      } else {
        // fallback: sem reserva previa, baixa direta
        UUID livroId = (UUID) it.get("livro_id");
        // baixa direta = quantidade--; movimentacao registrada pelo EstoqueService.movimentar? aqui faremos consumo minimo:
        jdbc.update("update estoques set quantidade=quantidade-? where livro_id=? and local_id=?",
            ((Number) it.get("quantidade")).intValue(), livroId, (UUID) venda.get("local_id"));
        // movimentacao
        jdbc.update("""
          insert into movimentacoes_estoque (id, livro_id, local_id, tipo, quantidade, motivo, origem_venda_item_id, criado_em)
          values (gen_random_uuid(), ?, ?, 'SAIDA', ?, 'venda', ?, now())
        """, livroId, (UUID) venda.get("local_id"), ((Number) it.get("quantidade")).intValue(), (UUID) it.get("id"));
      }
    }

    jdbc.update("update vendas set status='FINALIZADA', data_venda=now(), updated_at=now() where id=?", vendaId);
    return getVenda(vendaId);
  }

  @Transactional
  public Map<String,Object> cancelar(UUID vendaId){
    var venda = getVendaOrThrow(vendaId);
    if("FINALIZADA".equals(venda.get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Venda finalizada: utilize estorno");

    // libera reservas
    for (var it : itens(vendaId)){
      if(it.get("reserva_id") != null){
        estoque.liberar((UUID) it.get("reserva_id"));
      }
    }
    jdbc.update("update vendas set status='CANCELADA', updated_at=now() where id=?", vendaId);
    return getVenda(vendaId);
  }

  @Transactional
  public Map<String,Object> estornar(UUID vendaId){
    var venda = getVendaOrThrow(vendaId);
    if(!"FINALIZADA".equals(venda.get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Apenas vendas finalizadas podem ser estornadas");

    // devolve estoque (entrada)
    for (var it : itens(vendaId)){
      UUID livroId = (UUID) it.get("livro_id");
      int qtd = ((Number) it.get("quantidade")).intValue();
      jdbc.update("update estoques set quantidade = quantidade + ? where livro_id=? and local_id=?",
          qtd, livroId, (UUID) venda.get("local_id"));
      jdbc.update("""
        insert into movimentacoes_estoque (id, livro_id, local_id, tipo, quantidade, motivo, origem_venda_item_id, criado_em)
        values (gen_random_uuid(), ?, ?, 'ENTRADA', ?, 'estorno', ?, now())
        """, livroId, (UUID) venda.get("local_id"), qtd, (UUID) it.get("id"));
    }
    jdbc.update("update vendas set status='ESTORNADA', updated_at=now() where id=?", vendaId);
    return getVenda(vendaId);
  }

  // ========== HELPERS =========================================================

  private Map<String,Object> getVenda(UUID id){
    try {
      return jdbc.queryForMap("select * from vendas where id=?", id);
    } catch (EmptyResultDataAccessException e){
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venda nao encontrada");
    }
  }
  private Map<String,Object> getVendaOrThrow(UUID id){ return getVenda(id); }

  private Map<String,Object> getItem(UUID id){
    try {
      return jdbc.queryForMap("select * from itens_venda where id=?", id);
    } catch (EmptyResultDataAccessException e){
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nao encontrado");
    }
  }
  private Map<String,Object> getItemOrThrow(UUID id){ return getItem(id); }

  private List<Map<String,Object>> itens(UUID vendaId){
    return jdbc.queryForList("select * from itens_venda where venda_id=?", vendaId);
  }

  private void assertAberta(Map<String,Object> venda){
    String st = String.valueOf(venda.get("status"));
    if(!"ABERTA".equals(st))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Venda nao esta ABERTA");
  }

  private void assertAbertaOuPago(Map<String,Object> venda){
    String st = String.valueOf(venda.get("status"));
    if(!( "ABERTA".equals(st) || "PAGO".equals(st) || "PAGO_PARCIAL".equals(st) ))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Status invalido para finalizar: "+st);
  }

  private void recalc(UUID vendaId){
    // subtotal = soma (preco_unitario * quantidade)
    BigDecimal subtotal = njdbc.queryForObject("""
      select coalesce(sum(preco_unitario * quantidade),0) from itens_venda where venda_id=:v
      """, new MapSqlParameterSource("v", vendaId), BigDecimal.class);

    // desconto_itens
    BigDecimal descontoItens = njdbc.queryForObject("""
      select coalesce(sum(desconto_valor),0) from itens_venda where venda_id=:v
      """, new MapSqlParameterSource("v", vendaId), BigDecimal.class);

    // desconto_total ja contempla cupom (somado ao campo)
    BigDecimal descontoAtual = njdbc.queryForObject("""
      select coalesce(desconto_total,0) from vendas where id=:v
      """, new MapSqlParameterSource("v", vendaId), BigDecimal.class);

    BigDecimal frete = njdbc.queryForObject("""
      select coalesce(frete_total,0) from vendas where id=:v
      """, new MapSqlParameterSource("v", vendaId), BigDecimal.class);

    BigDecimal total = subtotal.subtract(descontoItens).subtract(descontoAtual).add(frete);
    if(total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

    jdbc.update("update vendas set subtotal=?, total=?, updated_at=now() where id=?",
        subtotal, total, vendaId);
  }

  private BigDecimal totalPago(UUID vendaId){
    return njdbc.queryForObject("""
      select coalesce(sum(valor),0) from pagamentos_venda where venda_id=:v and status='APROVADO'
      """, new MapSqlParameterSource("v", vendaId), BigDecimal.class);
  }

  private BigDecimal totalVenda(UUID vendaId){
    return njdbc.queryForObject("""
      select coalesce(total,0) from vendas where id=:v
      """, new MapSqlParameterSource("v", vendaId), BigDecimal.class);
  }

  private static BigDecimal toBd(Object o){
    if(o == null) return BigDecimal.ZERO;
    if(o instanceof BigDecimal b) return b;
    return new BigDecimal(String.valueOf(o));
  }
  private static BigDecimal nullSafe(BigDecimal b){ return b==null ? BigDecimal.ZERO : b; }
}
