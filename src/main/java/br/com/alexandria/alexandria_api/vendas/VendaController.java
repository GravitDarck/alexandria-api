package br.com.alexandria.alexandria_api.vendas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
public class VendaController {

  private final VendaService vendaService;

  public record NovaVenda(@NotNull UUID clienteId,
                          @NotNull UUID funcionarioId,
                          @NotNull UUID localId,
                          String origem,
                          String observacao) {}

  @PostMapping
  public Map<String, Object> abrir(@RequestBody @Valid NovaVenda request) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("clienteId", request.clienteId());
    payload.put("funcionarioId", request.funcionarioId());
    payload.put("localId", request.localId());
    payload.put("origem", request.origem());
    payload.put("observacao", request.observacao());
    return vendaService.abrir(payload);
  }

  public record ItemReq(@NotNull UUID livroId,
                        int quantidade,
                        BigDecimal precoUnit,
                        BigDecimal desconto) {}

  @PostMapping("/{id}/itens")
  public Map<String, Object> adicionarItem(@PathVariable UUID id, @RequestBody @Valid ItemReq request) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("livroId", request.livroId());
    payload.put("quantidade", request.quantidade());
    payload.put("precoUnit", request.precoUnit());
    payload.put("desconto", request.desconto());
    return vendaService.addItem(id, payload);
  }

  @PatchMapping("/{id}/itens/{itemId}")
  public Map<String, Object> atualizarItem(@PathVariable UUID id,
                                           @PathVariable UUID itemId,
                                           @RequestBody @Valid ItemReq request) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("livroId", request.livroId());
    payload.put("quantidade", request.quantidade());
    payload.put("precoUnit", request.precoUnit());
    payload.put("desconto", request.desconto());
    return vendaService.updateItem(id, itemId, payload);
  }

  @DeleteMapping("/{id}/itens/{itemId}")
  public void removerItem(@PathVariable UUID id, @PathVariable UUID itemId) {
    vendaService.removeItem(id, itemId);
  }

  @PostMapping("/{id}/cupom/{codigo}")
  public Map<String, Object> aplicarCupom(@PathVariable UUID id, @PathVariable String codigo) {
    return vendaService.aplicarCupom(id, codigo);
  }

  public record FreteReq(String modalidade,
                         String cepOrigem,
                         String cepDestino,
                         BigDecimal valor,
                         Integer prazoDias,
                         String codigoRastreio) {}

  @PostMapping("/{id}/frete")
  public Map<String, Object> definirFrete(@PathVariable UUID id, @RequestBody @Valid FreteReq request) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("modalidade", request.modalidade());
    payload.put("cepOrigem", request.cepOrigem());
    payload.put("cepDestino", request.cepDestino());
    payload.put("valor", request.valor());
    payload.put("prazoDias", request.prazoDias());
    payload.put("codigoRastreio", request.codigoRastreio());
    return vendaService.definirFrete(id, payload);
  }

  public record PagReq(@NotNull UUID formaId,
                       BigDecimal valor,
                       String transacaoRef) {}

  @PostMapping("/{id}/pagamentos")
  public Map<String, Object> registrarPagamento(@PathVariable UUID id, @RequestBody @Valid PagReq request) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("formaId", request.formaId());
    payload.put("valor", request.valor());
    payload.put("transacaoRef", request.transacaoRef());
    return vendaService.registrarPagamento(id, payload);
  }

  @PostMapping("/{id}/finalizar")
  public Map<String, Object> finalizar(@PathVariable UUID id) {
    return vendaService.finalizar(id);
  }

  @PostMapping("/{id}/cancelar")
  public Map<String, Object> cancelar(@PathVariable UUID id) {
    return vendaService.cancelar(id);
  }

  @PostMapping("/{id}/estornar")
  public Map<String, Object> estornar(@PathVariable UUID id) {
    return vendaService.estornar(id);
  }
}
