package io.phasetwo.keycloak.events;

import io.phasetwo.keycloak.events.auth.OAuth2ClientCredentials;
import io.phasetwo.keycloak.model.WebhookModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.keycloak.representations.idm.ComponentRepresentation;

/**
 * A webhook's {@code config}: payload format options and the settings of the config-driven
 * authentication types.
 *
 * <ul>
 *   <li>{@link WebhookModel#AUTH_TYPE_HEADERS}: every {@code header.<Name>} entry is sent as request
 *       header {@code <Name>}.
 *   <li>{@link WebhookModel#AUTH_TYPE_OAUTH2}: {@code oauth2.tokenUrl}, {@code oauth2.clientId},
 *       {@code oauth2.clientSecret} and optionally {@code oauth2.scope}.
 * </ul>
 *
 * Header values and the client secret are credentials: the webhook REST API returns them masked as
 * {@link #SECRET_MASK}, and a masked value sent back keeps the stored one.
 */
public final class WebhookConfig {

  /** What secret values are replaced with in API responses (Keycloak's own secret mask). */
  public static final String SECRET_MASK = ComponentRepresentation.SECRET_VALUE;

  public static final String HEADER_PREFIX = "header.";
  public static final String OAUTH2_TOKEN_URL = "oauth2.tokenUrl";
  public static final String OAUTH2_CLIENT_ID = "oauth2.clientId";
  public static final String OAUTH2_CLIENT_SECRET = "oauth2.clientSecret";
  public static final String OAUTH2_SCOPE = "oauth2.scope";

  private final Map<String, String> values;

  /**
   * @param values the config entries; null means none. Null keys and values are dropped.
   */
  public WebhookConfig(Map<String, String> values) {
    Map<String, String> copy = new LinkedHashMap<>();
    if (values != null) {
      values.forEach(
          (k, v) -> {
            if (k != null && v != null) copy.put(k, v);
          });
    }
    this.values = Collections.unmodifiableMap(copy);
  }

  /**
   * The entries.
   *
   * @return an unmodifiable map of the entries
   */
  public Map<String, String> asMap() {
    return values;
  }

  /**
   * The {@code header.<Name>} entries.
   *
   * @return header name to value
   */
  public Map<String, String> headers() {
    Map<String, String> headers = new LinkedHashMap<>();
    values.forEach(
        (k, v) -> {
          if (k.startsWith(HEADER_PREFIX) && k.length() > HEADER_PREFIX.length()) {
            headers.put(k.substring(HEADER_PREFIX.length()), v);
          }
        });
    return headers;
  }

  /**
   * The {@code oauth2.*} entries.
   *
   * @return the client credentials, possibly incomplete (see {@link
   *     OAuth2ClientCredentials#validate()})
   */
  public OAuth2ClientCredentials oauth2ClientCredentials() {
    return new OAuth2ClientCredentials(
        values.get(OAUTH2_TOKEN_URL),
        values.get(OAUTH2_CLIENT_ID),
        values.get(OAUTH2_CLIENT_SECRET),
        values.get(OAUTH2_SCOPE));
  }

  /**
   * Whether a key holds an authentication secret.
   *
   * @param key a config key
   * @return true for header values and the OAuth2 client secret
   */
  public boolean isSecret(String key) {
    return key != null && (key.startsWith(HEADER_PREFIX) || OAUTH2_CLIENT_SECRET.equals(key));
  }

  /**
   * Check the config has what an authentication type needs. Types that don't read the config
   * (hmac, bearer, none) always pass.
   *
   * @param authType the webhook's auth type
   * @throws IllegalArgumentException with a user-facing message if the config is insufficient
   */
  public void validateFor(String authType) {
    if (WebhookModel.AUTH_TYPE_HEADERS.equals(authType) && headers().isEmpty()) {
      throw new IllegalArgumentException(
          "authType headers requires at least one config entry named header.<Header-Name>");
    }
    if (WebhookModel.AUTH_TYPE_OAUTH2.equals(authType)) {
      oauth2ClientCredentials().validate();
    }
  }

  /**
   * Apply an update from the REST API. A null update leaves the config unchanged. Otherwise the
   * update replaces it, except that a masked value keeps the stored one, so a representation read
   * from the API can be sent back unchanged.
   *
   * @param update the incoming config, or null
   * @return the resulting config
   */
  public WebhookConfig merge(Map<String, String> update) {
    if (update == null) return this;
    Map<String, String> merged = new LinkedHashMap<>();
    new WebhookConfig(update)
        .values.forEach(
            (k, v) -> {
              if (!SECRET_MASK.equals(v)) merged.put(k, v);
              else if (values.containsKey(k)) merged.put(k, values.get(k));
            });
    return new WebhookConfig(merged);
  }

  /**
   * The entries for an API response, with every secret masked.
   *
   * @param formatSecret which keys the webhook's payload format declares secret
   * @return the entries, sorted by key
   */
  public Map<String, String> masked(Predicate<String> formatSecret) {
    Map<String, String> out = new TreeMap<>();
    values.forEach((k, v) -> out.put(k, isSecret(k) || formatSecret.test(k) ? SECRET_MASK : v));
    return out;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof WebhookConfig other && values.equals(other.values);
  }

  @Override
  public int hashCode() {
    return values.hashCode();
  }

  /** Never includes values, which may be credentials. */
  @Override
  public String toString() {
    return "WebhookConfig" + values.keySet();
  }
}
