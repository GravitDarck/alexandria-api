package br.com.alexandria.alexandria_api.util;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Utilitarios para trabalhar com JdbcTemplate / NamedParameterJdbcTemplate.
 * Evita boilerplate (Optional em queryForObject/map, IN-clause dinamica,
 * LIKE seguro, conversoes de tipos e helpers de paginacao).
 */
public final class JdbcUtils {

  private JdbcUtils() {
  }

  // ====================== Optional Queries ====================================

  public static Optional<Map<String, Object>> queryForMapOptional(JdbcTemplate jdbcTemplate,
                                                                  String sql,
                                                                  Object... args) {
    try {
      return Optional.of(jdbcTemplate.queryForMap(sql, args));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  public static <T> Optional<T> queryForObjectOptional(JdbcTemplate jdbcTemplate,
                                                       String sql,
                                                       Class<T> type,
                                                       Object... args) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(sql, type, args));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  public static Optional<Map<String, Object>> queryForMapOptional(NamedParameterJdbcTemplate jdbcTemplate,
                                                                  String sql,
                                                                  MapSqlParameterSource params) {
    try {
      return Optional.of(jdbcTemplate.queryForMap(sql, params));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  public static <T> Optional<T> queryForObjectOptional(NamedParameterJdbcTemplate jdbcTemplate,
                                                       String sql,
                                                       MapSqlParameterSource params,
                                                       Class<T> type) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, type));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  // ====================== SQL Helpers =========================================

  /**
   * Gera uma clausula IN segura para NamedParameterJdbcTemplate e vincula os valores
   * no MapSqlParameterSource.
   * Ex.: bindInClause("id", "ids", List.of(1, 2), params) -> "id in (:ids_0,:ids_1)".
   * Se a colecao estiver vazia, retorna "1=0" para evitar retornar tudo.
   */
  public static String bindInClause(String column,
                                    String baseParamName,
                                    Collection<?> values,
                                    MapSqlParameterSource params) {
    if (values == null || values.isEmpty()) {
      return "1=0";
    }
    List<String> names = new ArrayList<>(values.size());
    int i = 0;
    for (Object value : values) {
      String param = baseParamName + "_" + i++;
      names.add(":" + param);
      params.addValue(param, value);
    }
    return column + " in (" + String.join(",", names) + ")";
  }

  /**
   * Adiciona um parametro LIKE com escaping (% e _), retornando o marcador do parametro.
   * Uso:
   *   String like = JdbcUtils.likeContains("q", termo, params); // params.addValue("q", "%termo%")
   *   "... where col ilike " + like
   */
  public static String likeContains(String paramName, String term, MapSqlParameterSource params) {
    String escaped = term == null ? "" : term
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_");
    params.addValue(paramName, "%" + escaped + "%");
    return ":" + paramName;
  }

  /** Calcula o offset dado page/size, garantindo limites minimos. */
  public static int offset(int page, int size) {
    int p = Math.max(page, 0);
    int s = Math.max(size, 1);
    return p * s;
  }

  /** Monta um payload de paginacao padrao. */
  public static Map<String, Object> pageResponse(int page,
                                                 int size,
                                                 int totalElements,
                                                 List<?> content) {
    int s = Math.max(size, 1);
    int totalPages = (int) Math.ceil(totalElements / (double) s);
    return Map.of(
        "content", content,
        "page", Math.max(page, 0),
        "size", s,
        "totalElements", totalElements,
        "totalPages", totalPages
    );
  }

  // ====================== Type Conversions ====================================

  public static UUID toUUID(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof UUID uuid) {
      return uuid;
    }
    return UUID.fromString(String.valueOf(value));
  }

  public static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    return new BigDecimal(String.valueOf(value));
  }

  public static Integer toInteger(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.valueOf(String.valueOf(value));
  }

  public static Long toLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.valueOf(String.valueOf(value));
  }

  public static boolean toBoolean(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }
}
