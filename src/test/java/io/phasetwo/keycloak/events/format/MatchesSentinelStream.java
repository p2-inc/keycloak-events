package io.phasetwo.keycloak.events.format;

import io.phasetwo.keycloak.events.format.AzureSentinelPayloadFormatter.Column;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/**
 * Matches Logs Ingestion API rows that fit the declared stream: every row has {@code
 * TimeGenerated}, and only declared columns, each with a value of the declared type.
 */
public class MatchesSentinelStream extends TypeSafeDiagnosingMatcher<List<Map<String, Object>>> {

  private final Map<String, String> types =
      AzureSentinelPayloadFormatter.COLUMNS.stream()
          .collect(Collectors.toMap(Column::name, Column::type));

  @Override
  public void describeTo(Description description) {
    description.appendText("rows of the declared stream columns ").appendValue(types.keySet());
  }

  @Override
  protected boolean matchesSafely(List<Map<String, Object>> rows, Description mismatch) {
    for (Map<String, Object> row : rows) {
      if (!row.containsKey("TimeGenerated")) {
        mismatch.appendText("a row without TimeGenerated: ").appendValue(row);
        return false;
      }
      for (Map.Entry<String, Object> e : row.entrySet()) {
        String type = types.get(e.getKey());
        if (type == null) {
          mismatch.appendText("undeclared column ").appendValue(e.getKey());
          return false;
        }
        if (!hasType(e.getValue(), type)) {
          mismatch
              .appendText("column ")
              .appendValue(e.getKey())
              .appendText(" is not a " + type + ": ")
              .appendValue(e.getValue());
          return false;
        }
      }
    }
    return true;
  }

  private boolean hasType(Object value, String type) {
    return switch (type) {
      case "string" -> value instanceof String;
      case "dynamic" -> value instanceof Map || value instanceof List;
      case "datetime" -> value instanceof String s && isInstant(s);
      default -> false;
    };
  }

  private boolean isInstant(String s) {
    try {
      Instant.parse(s);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }
}
