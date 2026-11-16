package io.phasetwo.keycloak.events.format;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The exact request a formatter wants sent.
 *
 * @param body the request body bytes; signed and hashed as-is
 * @param contentType the {@code Content-Type} header value
 * @param headers additional, non-secret request headers (e.g. {@code Content-Encoding}); never null
 */
public record FormattedPayload(byte[] body, String contentType, Map<String, String> headers) {

  public static final String APPLICATION_JSON = "application/json";

  public FormattedPayload {
    if (body == null) throw new IllegalArgumentException("body cannot be null");
    if (contentType == null) contentType = APPLICATION_JSON;
    headers = headers == null ? Map.of() : Map.copyOf(headers);
  }

  public static FormattedPayload json(String body) {
    return new FormattedPayload(body.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON, Map.of());
  }

  /** The body decoded as UTF-8, for logging and storage. */
  public String bodyAsString() {
    return new String(body, StandardCharsets.UTF_8);
  }
}
