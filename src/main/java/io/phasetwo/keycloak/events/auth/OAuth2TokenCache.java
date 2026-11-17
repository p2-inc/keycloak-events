package io.phasetwo.keycloak.events.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import java.io.IOException;
import java.time.Duration;
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
 * Obtains and caches OAuth2 client credentials access tokens for webhook delivery (e.g. Microsoft
 * Entra tokens for the Azure Monitor Logs Ingestion API). Tokens are cached per {@link
 * OAuth2ClientCredentials} and refreshed {@link #EXPIRY_SKEW_SECONDS} before they expire.
 *
 * <p>Concurrent callers for the same credentials share one in-flight fetch; callers for other
 * credentials (other webhooks, other realms) never wait on it. The fetch is bounded by the
 * configured timeout, and so is the wait on another caller's fetch, so a stalled token endpoint
 * holds up only the sends that need its token, and only for a bounded time.
 *
 * <p>Uses its own HTTP client rather than Keycloak's {@code HttpClientProvider}: fetches run on the
 * sender's background threads, and Keycloak's client has no timeouts by default.
 */
@JBossLog
public class OAuth2TokenCache {

  static final long EXPIRY_SKEW_SECONDS = 60;
  static final long DEFAULT_EXPIRES_IN_SECONDS = 300;

  private final Duration timeout;
  private final Map<OAuth2ClientCredentials, CachedToken> cache = new ConcurrentHashMap<>();
  private final Map<OAuth2ClientCredentials, CompletableFuture<CachedToken>> inFlight =
      new ConcurrentHashMap<>();

  private record CachedToken(String accessToken, long expiresAtMillis) {
    boolean valid() {
      return System.currentTimeMillis() < expiresAtMillis;
    }
  }

  /**
   * @param timeout the connect, connection-pool and read timeout for token requests
   */
  public OAuth2TokenCache(Duration timeout) {
    this.timeout = timeout;
  }

  /**
   * A valid access token for the credentials, fetching a new one if needed.
   *
   * @param credentials the client credentials
   * @return the access token
   * @throws IOException if no token could be obtained in time
   */
  public String getToken(OAuth2ClientCredentials credentials) throws IOException {
    CachedToken cached = cache.get(credentials);
    if (cached != null && cached.valid()) return cached.accessToken();

    CompletableFuture<CachedToken> mine = new CompletableFuture<>();
    CompletableFuture<CachedToken> other = inFlight.putIfAbsent(credentials, mine);
    if (other != null) return await(other, credentials);
    try {
      // another caller may have stored a token between the cache check and claiming the fetch
      cached = cache.get(credentials);
      CachedToken token = cached != null && cached.valid() ? cached : fetch(credentials);
      cache.put(credentials, token);
      mine.complete(token);
      return token.accessToken();
    } catch (IOException | RuntimeException e) {
      mine.completeExceptionally(e);
      throw e;
    } finally {
      inFlight.remove(credentials, mine);
    }
  }

  private String await(CompletableFuture<CachedToken> fetch, OAuth2ClientCredentials credentials)
      throws IOException {
    String tokenUrl = credentials.tokenUrl();
    try {
      // the fetch is bounded by connect + pool + read timeouts; allow for all three
      return fetch.get(timeout.toMillis() * 3, TimeUnit.MILLISECONDS).accessToken();
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

  /**
   * Drop the cached token, e.g. after the receiver rejected it with a 401.
   *
   * @param credentials the client credentials
   */
  public void invalidate(OAuth2ClientCredentials credentials) {
    cache.remove(credentials);
  }

  /** Drop every cached token. */
  public void clear() {
    cache.clear();
  }

  private CachedToken fetch(OAuth2ClientCredentials credentials) throws IOException {
    String tokenUrl = credentials.tokenUrl();
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
              .param("client_id", credentials.clientId())
              .param("client_secret", credentials.clientSecret());
      if (!Strings.isNullOrEmpty(credentials.scope())) request.param("scope", credentials.scope());
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
}
