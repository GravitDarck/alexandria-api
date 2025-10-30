package br.com.alexandria.alexandria_api.estoque;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/estoque")
@RequiredArgsConstructor
public class EstoqueController {

  private final EstoqueService estoqueService;

  public record MovReq(@NotNull UUID livroId,
                       @NotNull UUID localId,
                       @NotNull String tipo,
                       int quantidade,
                       String motivo) {}

  @PostMapping("/movimentar")
  public void movimentar(@RequestBody @Valid MovReq request) {
    estoqueService.movimentar(new EstoqueService.Movimentacao(
        request.livroId(),
        request.localId(),
        request.tipo(),
        request.quantidade(),
        request.motivo()
    ));
  }

  public record ReservaReq(@NotNull UUID livroId,
                           @NotNull UUID localId,
                           UUID vendaId,
                           int quantidade) {}

  @PostMapping("/reservar")
  public Map<String, Object> reservar(@RequestBody @Valid ReservaReq request) {
    return estoqueService.reservar(new EstoqueService.Reserva(
        request.livroId(),
        request.localId(),
        request.vendaId(),
        request.quantidade()
    ));
  }

  @PostMapping("/liberar/{reservaId}")
  public void liberar(@PathVariable UUID reservaId) {
    estoqueService.liberar(reservaId);
  }

  public record InventarioReq(@NotNull UUID localId, String observacao) {}

  @PostMapping("/inventario/abrir")
  public Map<String, Object> abrirInventario(@RequestBody @Valid InventarioReq request) {
    return estoqueService.abrirInventario(request.localId(), request.observacao());
  }

  @PostMapping("/inventario/{id}/contar")
  public void contarInventario(@PathVariable UUID id,
                               @RequestParam UUID livroId,
                               @RequestParam int quantidade) {
    estoqueService.contarItem(id, livroId, quantidade);
  }

  @PostMapping("/inventario/{id}/fechar")
  public void fecharInventario(@PathVariable UUID id) {
    estoqueService.fecharInventario(id);
  }
}
