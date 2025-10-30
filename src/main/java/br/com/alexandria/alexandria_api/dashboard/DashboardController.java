package br.com.alexandria.alexandria_api.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dash")
@RequiredArgsConstructor
public class DashboardController {

  private final JdbcTemplate jdbcTemplate;

  @PostMapping("/refresh")
  public void refresh() {
    jdbcTemplate.execute("select refresh_dashboards_concurrently()");
  }

  @GetMapping("/vendas-dia")
  public List<Map<String, Object>> vendasDia() {
    return jdbcTemplate.queryForList("select * from mv_dash_vendas_dia order by dia desc limit 60");
  }

  @GetMapping("/top-livros")
  public List<Map<String, Object>> topLivros() {
    return jdbcTemplate.queryForList("select * from mv_top_livros order by qtd_vendida desc limit 20");
  }

  @GetMapping("/estoque-baixo")
  public List<Map<String, Object>> estoqueBaixo() {
    return jdbcTemplate.queryForList("select * from mv_estoque_baixo");
  }

  @GetMapping("/ticket-medio")
  public List<Map<String, Object>> ticketMedio() {
    return jdbcTemplate.queryForList("select * from mv_ticket_medio order by dia desc limit 60");
  }
}
