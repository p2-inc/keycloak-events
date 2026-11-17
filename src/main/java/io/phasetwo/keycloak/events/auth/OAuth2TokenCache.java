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
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.client.config.RequestConfig;
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

  /** Connect, connection-pool and read timeout for token requests. Settable for tests. */
  static volatile Duration timeout = Duration.ofSeconds(10);

  private static final Map<String, CachedToken> CACHE = new ConcurrentHashMap<>();
  private static final Map<String, CompletableFuture<CachedToken>> IN_FLIGHT =
      new ConcurrentHashMap<>();

  private record CachedToken(String accessToken, long expiresAtMillis) {
    boolean valid() {
      return System.currentTimeMillis() < expiresAtMillis;
    }
  }

  private OAuth2TokenCache() {}

  /**
   * Return a valid access token for the webhook config, fetching a new one if needed.
   *
   * <p>Concurrent callers for the same credentials share one in-flight fetch; callers for other
   * credentials (other webhooks, other realms) never wait on it. The fetch itself is bounded by
   * {@link #timeout}, and so is the wait on another caller's fetch, so a stalled token endpoint
   * holds up only the sends that need its token, and only for a bounded time.
   */
  public static String getToken(Map<String, String> config) throws IOException {
    String key = cacheKey(config);
    CachedToken cached = CACHE.get(key);
    if (cached != null && cached.valid()) return cached.accessToken();

    CompletableFuture<CachedToken> mine = new CompletableFuture<>();
    CompletableFuture<CachedToken> inFlight = IN_FLIGHT.putIfAbsent(key, mine);
    if (inFlight != null) return await(inFlight, config);
    try {
      // another caller may have stored a token between the cache check and claiming the fetch
      cached = CACHE.get(key);
      CachedToken token = cached != null && cached.valid() ? cached : fetch(config);
      CACHE.put(key, token);
      mine.complete(token);
      return token.accessToken();
    } catch (IOException | RuntimeException e) {
      mine.completeExceptionally(e);
      throw e;
    } finally {
      IN_FLIGHT.remove(key, mine);
    }
  }

  private static String await(CompletableFuture<CachedToken> inFlight, Map<String, String> config)
      throws IOException {
    String tokenUrl = config.get(OAUTH2_TOKEN_URL);
    try {
      // the fetch is bounded by connect + pool + read timeouts; allow for all three
      return inFlight.get(timeout.toMillis() * 3, TimeUnit.MILLISECONDS).accessToken();
    } catch (TimeoutException e) {
      throw new IOException(String.format("timed out waiting for a token from %s", tokenUrl), e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException io) throw io;
      throw new IOException(
          String.format("token request to %s failed: %s", tokenUrl, cause), cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted waiting for a token from " + tokenUrl, e);
    }
  }

  /** Drop the cached token, e.g. after the receiver rejected it with a 401. */
  public static void invalidate(Map<String, String> config) {
    CACHE.remove(cacheKey(config));
  }

  static void clear() {
    CACHE.clear();
    IN_FLIGHT.clear();
  }

  private static CachedToken fetch(Map<String, String> config) throws IOException {
    String tokenUrl = config.get(OAUTH2_TOKEN_URL);
    int millis = (int) timeout.toMillis();
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(millis)
            .setConnectionRequestTimeout(millis)
            .setSocketTimeout(millis)
            .build();
    try (CloseableHttpClient http =
        HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
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
