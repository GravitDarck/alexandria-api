package br.com.alexandria.alexandria_api.estoque;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EstoqueService {

  private final JdbcTemplate jdbcTemplate;

  // ===================== Movimentacoes Manuais ===============================

  @Transactional
  public void movimentar(Movimentacao movimentacao) {
    UUID livroId = movimentacao.livroId();
    UUID localId = movimentacao.localId();
    int quantidade = movimentacao.quantidade();
    String tipo = movimentacao.tipo().toUpperCase(Locale.ROOT);
    String motivo = movimentacao.motivo();

    ensureEstoqueRow(livroId, localId);

    switch (tipo) {
      case "ENTRADA" -> {
        jdbcTemplate.update(
            "update estoques set quantidade = quantidade + ? where livro_id = ? and local_id = ?",
            quantidade,
            livroId,
            localId
        );
        registrarMovimentacao(livroId, localId, "ENTRADA", quantidade, motivo, null, null, null);
      }
      case "SAIDA" -> {
        int disponivel = getDisponivel(livroId, localId);
        if (disponivel < quantidade) {
          throw new ResponseStatusException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "Saldo insuficiente (disponivel=" + disponivel + ")"
          );
        }
        jdbcTemplate.update(
            "update estoques set quantidade = quantidade - ? where livro_id = ? and local_id = ?",
            quantidade,
            livroId,
            localId
        );
        registrarMovimentacao(livroId, localId, "SAIDA", quantidade, motivo, null, null, null);
      }
      case "AJUSTE" -> {
        jdbcTemplate.update(
            "update estoques set quantidade = quantidade + ? where livro_id = ? and local_id = ?",
            quantidade,
            livroId,
            localId
        );
        registrarMovimentacao(livroId, localId, "AJUSTE", quantidade, motivo, null, null, null);
      }
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo invalido: " + tipo);
    }
  }

  // ============================= Reservas ====================================

  @Transactional
  public Map<String, Object> reservar(Reserva reserva) {
    ensureEstoqueRow(reserva.livroId(), reserva.localId());

    int disponivel = getDisponivel(reserva.livroId(), reserva.localId());
    if (disponivel < reserva.quantidade()) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Saldo insuficiente (disponivel=" + disponivel + ")"
      );
    }

    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
            insert into reservas_estoque (id, livro_id, local_id, venda_id, quantidade, status, expira_em, created_at, updated_at)
            values (?,?,?,?,?,'ATIVA', ?, now(), now())
            """,
        id,
        reserva.livroId(),
        reserva.localId(),
        reserva.vendaId(),
        reserva.quantidade(),
        Timestamp.from(Instant.now().plusSeconds(60L * 60)) // 1h padrao
    );

    jdbcTemplate.update(
        "update estoques set reservado = reservado + ? where livro_id = ? and local_id = ?",
        reserva.quantidade(),
        reserva.livroId(),
        reserva.localId()
    );

    return Map.of(
        "id", id,
        "livro_id", reserva.livroId(),
        "local_id", reserva.localId(),
        "quantidade", reserva.quantidade(),
        "status", "ATIVA"
    );
  }

  @Transactional
  public void liberar(UUID reservaId) {
    Map<String, Object> row = getReserva(reservaId);
    if (row == null || !"ATIVA".equals(row.get("status"))) {
      return;
    }

    jdbcTemplate.update(
        "update estoques set reservado = reservado - ? where livro_id = ? and local_id = ?",
        ((Number) row.get("quantidade")).intValue(),
        row.get("livro_id"),
        row.get("local_id")
    );

    jdbcTemplate.update(
        "update reservas_estoque set status = 'CANCELADA', updated_at = now() where id = ?",
        reservaId
    );
  }

  @Transactional
  public void consumir(UUID reservaId, UUID origemVendaItemId) {
    Map<String, Object> row = getReserva(reservaId);
    if (row == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva nao encontrada");
    }
    if (!"ATIVA".equals(row.get("status"))) {
      return;
    }

    UUID livroId = (UUID) row.get("livro_id");
    UUID localId = (UUID) row.get("local_id");
    int quantidade = ((Number) row.get("quantidade")).intValue();

    jdbcTemplate.update(
        "update estoques set reservado = reservado - ?, quantidade = quantidade - ? where livro_id = ? and local_id = ?",
        quantidade,
        quantidade,
        livroId,
        localId
    );

    registrarMovimentacao(livroId, localId, "SAIDA", quantidade, "venda", origemVendaItemId, null, null);
    jdbcTemplate.update(
        "update reservas_estoque set status = 'CONSUMIDA', updated_at = now() where id = ?",
        reservaId
    );
  }

  // ============================ Inventario ====================================

  @Transactional
  public Map<String, Object> abrirInventario(UUID localId, String observacao) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
            insert into inventarios (id, local_id, iniciado_em, status, observacao, created_at, updated_at)
            values (?,?, now(), 'ABERTO', ?, now(), now())
            """,
        id,
        localId,
        observacao
    );
    return Map.of("id", id, "status", "ABERTO");
  }

  @Transactional
  public void contarItem(UUID inventarioId, UUID livroId, int quantidade) {
    jdbcTemplate.update(
        """
            insert into itens_inventario (id, inventario_id, livro_id, quantidade_sistema, quantidade_contada, created_at, updated_at)
            values (gen_random_uuid(), ?, ?, coalesce((select quantidade from estoques where livro_id = ? and local_id = (select local_id from inventarios where id = ?)), 0), ?, now(), now())
            on conflict (inventario_id, livro_id)
            do update set quantidade_contada = excluded.quantidade_contada, updated_at = now()
            """,
        inventarioId,
        livroId,
        livroId,
        inventarioId,
        quantidade
    );
  }

  @Transactional
  public void fecharInventario(UUID inventarioId) {
    List<Map<String, Object>> itens = jdbcTemplate.queryForList(
        """
            select ii.livro_id,
                   ii.quantidade_sistema as sistema,
                   coalesce(ii.quantidade_contada, 0) as contada,
                   i.local_id
            from itens_inventario ii
            join inventarios i on i.id = ii.inventario_id
            where ii.inventario_id = ?
            """,
        inventarioId
    );

    for (Map<String, Object> item : itens) {
      UUID livroId = (UUID) item.get("livro_id");
      UUID localId = (UUID) item.get("local_id");
      int sistema = ((Number) item.get("sistema")).intValue();
      int contada = ((Number) item.get("contada")).intValue();
      int delta = contada - sistema;
      if (delta != 0) {
        ensureEstoqueRow(livroId, localId);
        jdbcTemplate.update(
            "update estoques set quantidade = quantidade + ? where livro_id = ? and local_id = ?",
            delta,
            livroId,
            localId
        );
        registrarMovimentacao(livroId, localId, "AJUSTE", delta, "inventario", null, null, null);
      }
    }
    jdbcTemplate.update(
        "update inventarios set finalizado_em = now(), status = 'FECHADO', updated_at = now() where id = ?",
        inventarioId
    );
  }

  // ================================ Helpers ===================================

  private void ensureEstoqueRow(UUID livroId, UUID localId) {
    jdbcTemplate.update(
        """
            insert into estoques (id, livro_id, local_id, quantidade, reservado, minimo, created_at, updated_at)
            values (gen_random_uuid(), ?, ?, 0, 0, 0, now(), now())
            on conflict (livro_id, local_id) do nothing
            """,
        livroId,
        localId
    );
  }

  private int getDisponivel(UUID livroId, UUID localId) {
    Integer disponivel = jdbcTemplate.queryForObject(
        """
            select coalesce(quantidade, 0) - coalesce(reservado, 0) as disponivel
            from estoques
            where livro_id = ? and local_id = ?
            """,
        Integer.class,
        livroId,
        localId
    );
    return disponivel == null ? 0 : disponivel;
  }

  private Map<String, Object> getReserva(UUID reservaId) {
    try {
      return jdbcTemplate.queryForMap("select * from reservas_estoque where id = ?", reservaId);
    } catch (EmptyResultDataAccessException ex) {
      return null;
    }
  }

  private void registrarMovimentacao(UUID livroId,
                                     UUID localId,
                                     String tipo,
                                     int quantidade,
                                     String motivo,
                                     UUID origemVendaItemId,
                                     UUID origemDevCliItemId,
                                     UUID origemDevFornId) {
    jdbcTemplate.update(
        """
            insert into movimentacoes_estoque (id, livro_id, local_id, tipo, quantidade, motivo, origem_venda_item_id, origem_dev_cli_item_id, origem_dev_forn_id, criado_em)
            values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, now())
            """,
        livroId,
        localId,
        tipo,
        quantidade,
        motivo,
        origemVendaItemId,
        origemDevCliItemId,
        origemDevFornId
    );
  }

  public record Movimentacao(UUID livroId, UUID localId, String tipo, int quantidade, String motivo) {}

  public record Reserva(UUID livroId, UUID localId, UUID vendaId, int quantidade) {}
}
