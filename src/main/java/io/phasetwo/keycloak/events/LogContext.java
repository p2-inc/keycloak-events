package io.phasetwo.keycloak.events;

import java.util.HashMap;
import java.util.Map;
import org.jboss.logging.MDC;

/**
 * AutoCloseable helper that applies a set of key/value pairs to the JBoss {@link MDC} and restores
 * the previous values on close. Used to scope event fields to a single log statement on reused
 * executor threads.
 */
public final class LogContext implements AutoCloseable {

  private final Map<String, Object> previousValues;

  private LogContext(Map<String, Object> values, String prefix) {
    this.previousValues = new HashMap<>();
    String safePrefix = prefix == null ? "" : prefix;
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String key = safePrefix + entry.getKey();
      previousValues.put(key, MDC.get(key));
      MDC.put(key, entry.getValue());
    }
  }

  public static LogContext with(Map<String, Object> values, String prefix) {
    return new LogContext(values, prefix);
  }

  public static LogContext with(FlatEvent event, String prefix) {
    return new LogContext(event.toMap(), prefix);
  }

  public static LogContext with(FlatAdminEvent event, String prefix) {
    return new LogContext(event.toMap(), prefix);
  }

  @Override
  public void close() {
    previousValues.forEach(
        (key, oldValue) -> {
          if (oldValue == null) {
            MDC.remove(key);
          } else {
            MDC.put(key, oldValue);
          }
        });
  }
}
