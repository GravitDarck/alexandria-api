package br.com.alexandria.alexandria_api.generic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SqlBuilder {

  private static final Pattern COLUMN_PATTERN = Pattern.compile("^[a-z0-9_]+$", Pattern.CASE_INSENSITIVE);

  private SqlBuilder() {
  }

  /**
   * Sanitiza a clausula "order by" recebido do cliente permitindo apenas colunas whitelisted
   * e direcoes ASC ou DESC. Ex.: "nome asc, created_at desc".
   */
  public static String sanitizeSort(String sortParam, Set<String> allowedColumns, String defaultSort) {
    if (sortParam == null || sortParam.isBlank()) {
      return defaultSort;
    }
    List<String> parts = Arrays.stream(sortParam.split(","))
        .map(String::trim)
        .map(part -> part.replaceAll("\\s+", " "))
        .toList();

    List<String> valid = new ArrayList<>();
    for (String part : parts) {
      String[] tokens = part.split(" ");
      String column = tokens[0].trim();
      String direction = tokens.length > 1 ? tokens[1].trim().toUpperCase(Locale.ROOT) : "ASC";

      if (!COLUMN_PATTERN.matcher(column).matches()) {
        continue;
      }
      if (!(direction.equals("ASC") || direction.equals("DESC"))) {
        direction = "ASC";
      }
      if (!allowedColumns.contains(column)) {
        continue;
      }

      valid.add(column + " " + direction);
    }
    if (valid.isEmpty()) {
      return defaultSort;
    }
    return valid.stream().distinct().collect(Collectors.joining(", "));
  }

  /**
   * Filtra o mapa do corpo mantendo apenas colunas permitidas (e opcionalmente removendo a PK).
   */
  public static Map<String, Object> filterColumns(Map<String, Object> body, Set<String> allowed, String pk) {
    Map<String, Object> filtered = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : body.entrySet()) {
      String key = entry.getKey();
      if (!allowed.contains(key)) {
        continue;
      }
      if (pk != null && pk.equals(key)) {
        continue;
      }
      filtered.put(key, entry.getValue());
    }
    return filtered;
  }
}
