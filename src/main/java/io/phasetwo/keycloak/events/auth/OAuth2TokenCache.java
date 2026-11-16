package io.phasetwo.keycloak.events.auth;

import static io.phasetwo.keycloak.events.auth.WebhookAuthConfig.OAUTH2_CLIENT_ID;
import static io.phasetwo.keycloak.events.auth.WebhookAuthConfig.OAUTH2_CLIENT_SECRET;
import static io.phasetwo.keycloak.events.auth.WebhookAuthConfig.OAUTH2_SCOPE;
import static io.phasetwo.keycloak.events.auth.WebhookAuthConfig.OAUTH2_TOKEN_URL;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.broker.provider.util.LegacySimpleHttp;

/**
 * Obtains and caches OAuth2 client-credentials access tokens for webhook delivery (e.g. Microsoft
 * Entra tokens for the Azure Monitor Logs Ingestion API). Tokens are cached per token endpoint,
 * client, secret and scope, and refreshed {@link #EXPIRY_SKEW_SECONDS} before they expire.
 */
@JBossLog
public final class OAuth2TokenCache {

  static final long EXPIRY_SKEW_SECONDS = 60;
  static final long DEFAULT_EXPIRES_IN_SECONDS = 300;

  private static final Map<String, CachedToken> CACHE = new ConcurrentHashMap<>();

  private record CachedToken(String accessToken, long expiresAtMillis) {
    boolean valid() {
      return System.currentTimeMillis() < expiresAtMillis;
    }
  }

  private OAuth2TokenCache() {}

  /** Return a valid access token for the webhook config, fetching a new one if needed. */
  public static String getToken(Map<String, String> config) throws IOException {
    String key = cacheKey(config);
    CachedToken cached = CACHE.get(key);
    if (cached != null && cached.valid()) return cached.accessToken();
    synchronized (OAuth2TokenCache.class) {
      cached = CACHE.get(key);
      if (cached != null && cached.valid()) return cached.accessToken();
      CachedToken fresh = fetch(config);
      CACHE.put(key, fresh);
      return fresh.accessToken();
    }
  }

  /** Drop the cached token, e.g. after the receiver rejected it with a 401. */
  public static void invalidate(Map<String, String> config) {
    CACHE.remove(cacheKey(config));
  }

  static void clear() {
    CACHE.clear();
  }

  private static CachedToken fetch(Map<String, String> config) throws IOException {
    String tokenUrl = config.get(OAUTH2_TOKEN_URL);
    try (CloseableHttpClient http = HttpClients.createDefault()) {
      LegacySimpleHttp request =
          LegacySimpleHttp.doPost(tokenUrl, http)
              .param("grant_type", "client_credentials")
              .param("client_id", config.get(OAUTH2_CLIENT_ID))
              .param("client_secret", config.get(OAUTH2_CLIENT_SECRET));
      String scope = config.get(OAUTH2_SCOPE);
      if (!Strings.isNullOrEmpty(scope)) request.param("scope", scope);
      try (LegacySimpleHttp.Response response = request.asResponse()) {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
          throw new IOException(
              String.format("token request to %s failed with HTTP %d", tokenUrl, status));
        }
        JsonNode json = response.asJson();
        JsonNode token = json.get("access_token");
        if (token == null || token.asText().isEmpty()) {
          throw new IOException(String.format("no access_token in response from %s", tokenUrl));
        }
        long expiresIn =
            json.hasNonNull("expires_in")
                ? json.get("expires_in").asLong(DEFAULT_EXPIRES_IN_SECONDS)
                : DEFAULT_EXPIRES_IN_SECONDS;
        long ttl = Math.max(0, expiresIn - EXPIRY_SKEW_SECONDS);
        log.debugf("fetched webhook oauth2 token from %s (expires in %ds)", tokenUrl, expiresIn);
        return new CachedToken(token.asText(), System.currentTimeMillis() + ttl * 1000);
      }
    }
  }

  private static String cacheKey(Map<String, String> config) {
    String raw =
        String.join(
            "\n",
            nz(config.get(OAUTH2_TOKEN_URL)),
            nz(config.get(OAUTH2_CLIENT_ID)),
            nz(config.get(OAUTH2_CLIENT_SECRET)),
            nz(config.get(OAUTH2_SCOPE)));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
