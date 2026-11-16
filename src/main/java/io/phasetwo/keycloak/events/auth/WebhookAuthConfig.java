package io.phasetwo.keycloak.events.auth;

import com.google.common.base.Strings;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config keys for the authentication types that are driven by a webhook's {@code config} map rather
 * than its {@code secret}.
 *
 * <ul>
 *   <li>{@code headers}: every {@code header.<Name>} entry is sent as a request header {@code
 *       <Name>}. Used for API-key style ingestion endpoints (e.g. {@code DD-API-KEY}).
 *   <li>{@code oauth2-client-credentials}: a bearer token is obtained from {@code oauth2.tokenUrl}
 *       with the client credentials grant and cached until shortly before it expires.
 * </ul>
 */
public final class WebhookAuthConfig {

  public static final String AUTH_TYPE_HEADERS = "headers";
  public static final String AUTH_TYPE_OAUTH2 = "oauth2-client-credentials";

  public static final String HEADER_PREFIX = "header.";
  public static final String OAUTH2_TOKEN_URL = "oauth2.tokenUrl";
  public static final String OAUTH2_CLIENT_ID = "oauth2.clientId";
  public static final String OAUTH2_CLIENT_SECRET = "oauth2.clientSecret";
  public static final String OAUTH2_SCOPE = "oauth2.scope";

  private WebhookAuthConfig() {}

  /** Header auth values and client secrets are credentials. */
  public static boolean isSecretKey(String key) {
    return key != null && (key.startsWith(HEADER_PREFIX) || OAUTH2_CLIENT_SECRET.equals(key));
  }

  /** The {@code header.<Name>} entries as a header name to value map. */
  public static Map<String, String> headers(Map<String, String> config) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (config == null) return headers;
    config.forEach(
        (k, v) -> {
          if (k != null && k.startsWith(HEADER_PREFIX) && k.length() > HEADER_PREFIX.length()) {
            headers.put(k.substring(HEADER_PREFIX.length()), v);
          }
        });
    return headers;
  }

  /** Validate the config required by an auth type. Throws IllegalArgumentException if invalid. */
  public static void validate(String authType, Map<String, String> config) {
    Map<String, String> c = config == null ? Map.of() : config;
    if (AUTH_TYPE_HEADERS.equals(authType)) {
      if (headers(c).isEmpty()) {
        throw new IllegalArgumentException(
            "authType headers requires at least one config entry named header.<Header-Name>");
      }
    } else if (AUTH_TYPE_OAUTH2.equals(authType)) {
      for (String k : new String[] {OAUTH2_TOKEN_URL, OAUTH2_CLIENT_ID, OAUTH2_CLIENT_SECRET}) {
        if (Strings.isNullOrEmpty(c.get(k))) {
          throw new IllegalArgumentException(
              String.format("authType %s requires config %s", AUTH_TYPE_OAUTH2, k));
        }
      }
      try {
        new URI(c.get(OAUTH2_TOKEN_URL)).parseServerAuthority();
      } catch (Exception e) {
        throw new IllegalArgumentException(OAUTH2_TOKEN_URL + " is not a URL");
      }
    }
  }
}
