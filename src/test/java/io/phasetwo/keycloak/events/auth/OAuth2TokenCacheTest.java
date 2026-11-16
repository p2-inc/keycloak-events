package io.phasetwo.keycloak.events.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.phasetwo.keycloak.Helpers;
import io.phasetwo.keycloak.RecordingServer;
import io.phasetwo.keycloak.RecordingServer.Captured;
import io.phasetwo.keycloak.RecordingServer.Reply;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OAuth2TokenCacheTest {

  RecordingServer server;
  Map<String, String> config;
  AtomicInteger issued = new AtomicInteger();
  AtomicInteger status = new AtomicInteger(200);

  @BeforeEach
  void setUp() throws Exception {
    OAuth2TokenCache.clear();
    int port = Helpers.nextFreePort(18201, 18300);
    server =
        new RecordingServer(port)
            .post(
                "/token",
                c ->
                    status.get() == 200
                        ? Reply.json(
                            200,
                            "{\"access_token\":\"tok-"
                                + issued.incrementAndGet()
                                + "\",\"token_type\":\"Bearer\",\"expires_in\":3599}")
                        : Reply.json(status.get(), "{\"error\":\"invalid_client\"}"))
            .start();
    config = new HashMap<>();
    config.put(WebhookAuthConfig.OAUTH2_TOKEN_URL, "http://localhost:" + port + "/token");
    config.put(WebhookAuthConfig.OAUTH2_CLIENT_ID, "client-a");
    config.put(WebhookAuthConfig.OAUTH2_CLIENT_SECRET, "secret a&b");
    config.put(WebhookAuthConfig.OAUTH2_SCOPE, "https://monitor.azure.com//.default");
  }

  @AfterEach
  void tearDown() {
    server.close();
    OAuth2TokenCache.clear();
  }

  @Test
  public void testFetchesOnceAndCaches() throws Exception {
    assertThat(OAuth2TokenCache.getToken(config), is("tok-1"));
    assertThat(OAuth2TokenCache.getToken(config), is("tok-1"));
    List<Captured> requests = server.drain();
    assertThat(requests, hasSize(1));
    String form = requests.get(0).bodyAsString();
    assertThat(form, containsString("grant_type=client_credentials"));
    assertThat(form, containsString("client_id=client-a"));
    assertThat(form, containsString("client_secret=secret+a%26b"));
    assertThat(form, containsString("scope=https%3A%2F%2Fmonitor.azure.com%2F%2F.default"));
    assertThat(
        requests.get(0).header("Content-Type"),
        containsString("application/x-www-form-urlencoded"));
  }

  @Test
  public void testInvalidateFetchesNewToken() throws Exception {
    assertThat(OAuth2TokenCache.getToken(config), is("tok-1"));
    OAuth2TokenCache.invalidate(config);
    assertThat(OAuth2TokenCache.getToken(config), is("tok-2"));
  }

  @Test
  public void testDifferentClientsGetDifferentTokens() throws Exception {
    assertThat(OAuth2TokenCache.getToken(config), is("tok-1"));
    Map<String, String> other = new HashMap<>(config);
    other.put(WebhookAuthConfig.OAUTH2_CLIENT_ID, "client-b");
    assertThat(OAuth2TokenCache.getToken(other), is("tok-2"));
    assertThat(OAuth2TokenCache.getToken(config), is("tok-1"));
  }

  @Test
  public void testTokenEndpointErrorThrows() throws Exception {
    status.set(401);
    assertThrows(IOException.class, () -> OAuth2TokenCache.getToken(config));
    server.await(Duration.ofSeconds(1), c -> true);
  }
}
