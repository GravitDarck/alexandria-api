package br.com.alexandria.alexandria_api.generic;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GenericCrudController {

  private final TableRegistry tableRegistry;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  @GetMapping("/{table}")
  public Map<String, Object> list(@PathVariable String table,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "created_at desc") String sort) {
    TableRegistry.TableDef definition = tableRegistry.get(table)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    String whereClause = (q != null && !q.isBlank()) ? " where cast(row_to_json(t) as text) ilike :q" : "";
    String orderClause = SqlBuilder.sanitizeSort(sort, definition.columns(), "created_at desc");
    String sql = "select * from " + definition.name() + " t" + whereClause + " order by " + orderClause + " offset :off limit :lim";

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("q", q == null ? null : "%" + q + "%")
        .addValue("off", page * size)
        .addValue("lim", size);

    List<Map<String, Object>> rows = namedJdbcTemplate.queryForList(sql, params);
    Integer total = namedJdbcTemplate.queryForObject(
        "select count(*) from " + definition.name() + (whereClause.isBlank() ? "" : whereClause),
        params,
        Integer.class
    );

    return Map.of(
        "content", rows,
        "page", page,
        "size", size,
        "totalElements", total == null ? 0 : total
    );
  }

  @GetMapping("/{table}/{id}")
  public Map<String, Object> get(@PathVariable String table, @PathVariable UUID id) {
    TableRegistry.TableDef definition = tableRegistry.get(table)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (definition.pk() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tabela sem chave primaria simples");
    }
    return namedJdbcTemplate.queryForMap(
        "select * from " + definition.name() + " where " + definition.pk() + " = :id",
        new MapSqlParameterSource("id", id)
    );
  }

  @PostMapping("/{table}")
  public Map<String, Object> create(@PathVariable String table, @RequestBody Map<String, Object> body) {
    TableRegistry.TableDef definition = tableRegistry.get(table)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!definition.updatable()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente leitura");
    }

    Map<String, Object> filtered = body.entrySet()
        .stream()
        .filter(entry -> definition.columns().contains(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (definition.pk() != null && !filtered.containsKey(definition.pk())) {
      filtered.put(definition.pk(), UUID.randomUUID());
    }

    String columns = String.join(",", filtered.keySet());
    String values = filtered.keySet().stream()
        .map(key -> ":" + key)
        .collect(Collectors.joining(","));

    String sql = "insert into " + definition.name() + " (" + columns + ") values (" + values + ") returning *";
    return namedJdbcTemplate.queryForMap(sql, new MapSqlParameterSource(filtered));
  }

  @PatchMapping("/{table}/{id}")
  public Map<String, Object> update(@PathVariable String table,
                                    @PathVariable UUID id,
                                    @RequestBody Map<String, Object> body) {
    TableRegistry.TableDef definition = tableRegistry.get(table)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!definition.updatable()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente leitura");
    }

    Map<String, Object> filtered = body.entrySet()
        .stream()
        .filter(entry -> definition.columns().contains(entry.getKey()) && !entry.getKey().equals(definition.pk()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (filtered.isEmpty()) {
      return get(table, id);
    }

    String setClause = filtered.keySet().stream()
        .map(key -> key + " = :" + key)
        .collect(Collectors.joining(","));

    String sql = "update " + definition.name() + " set " + setClause + " where " + definition.pk() + " = :id returning *";
    MapSqlParameterSource params = new MapSqlParameterSource(filtered).addValue("id", id);
    return namedJdbcTemplate.queryForMap(sql, params);
  }

  @DeleteMapping("/{table}/{id}")
  public void delete(@PathVariable String table, @PathVariable UUID id) {
    TableRegistry.TableDef definition = tableRegistry.get(table)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!definition.updatable()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente leitura");
    }
    namedJdbcTemplate.update(
        "delete from " + definition.name() + " where " + definition.pk() + " = :id",
        new MapSqlParameterSource("id", id)
    );
  }
}
