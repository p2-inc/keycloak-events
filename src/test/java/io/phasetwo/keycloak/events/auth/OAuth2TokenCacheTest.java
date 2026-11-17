package io.phasetwo.keycloak.events.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.phasetwo.keycloak.Helpers;
import io.phasetwo.keycloak.RecordingServer;
import io.phasetwo.keycloak.RecordingServer.Captured;
import io.phasetwo.keycloak.RecordingServer.Reply;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OAuth2TokenCacheTest {

  RecordingServer server;
  Map<String, String> config;
  AtomicInteger issued = new AtomicInteger();
  AtomicInteger status = new AtomicInteger(200);
  AtomicInteger delayMillis = new AtomicInteger();

  static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    OAuth2TokenCache.clear();
    int port = Helpers.nextFreePort(18201, 18300);
    server =
        new RecordingServer(port)
            .post(
                "/token",
                c -> {
                  sleep(delayMillis.get());
                  return status.get() == 200
                      ? Reply.json(
                          200,
                          "{\"access_token\":\"tok-"
                              + issued.incrementAndGet()
                              + "\",\"token_type\":\"Bearer\",\"expires_in\":3599}")
                      : Reply.json(status.get(), "{\"error\":\"invalid_client\"}");
                })
            .start();
    config = new HashMap<>();
    config.put(WebhookAuthConfig.OAUTH2_TOKEN_URL, "http://localhost:" + port + "/token");
    config.put(WebhookAuthConfig.OAUTH2_CLIENT_ID, "client-a");
    config.put(WebhookAuthConfig.OAUTH2_CLIENT_SECRET, "secret a&b");
    config.put(WebhookAuthConfig.OAUTH2_SCOPE, "https://monitor.azure.com//.default");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.close();
    if (stalled != null) stalled.close();
    OAuth2TokenCache.clear();
    OAuth2TokenCache.timeout = DEFAULT_TIMEOUT;
  }

  static final Duration DEFAULT_TIMEOUT = OAuth2TokenCache.timeout;

  StalledEndpoint stalled;

  /** Accepts connections and never answers, like a hung token endpoint. */
  static class StalledEndpoint implements AutoCloseable {
    final ServerSocket socket;
    final List<Socket> accepted = new CopyOnWriteArrayList<>();
    final Semaphore connections = new Semaphore(0);

    StalledEndpoint() throws IOException {
      socket = new ServerSocket(0);
      Thread t =
          new Thread(
              () -> {
                try {
                  while (true) {
                    accepted.add(socket.accept());
                    connections.release();
                  }
                } catch (IOException closed) {
                }
              });
      t.setDaemon(true);
      t.start();
    }

    Map<String, String> config(String clientId) {
      Map<String, String> c = new HashMap<>();
      c.put(
          WebhookAuthConfig.OAUTH2_TOKEN_URL,
          "http://localhost:" + socket.getLocalPort() + "/token");
      c.put(WebhookAuthConfig.OAUTH2_CLIENT_ID, clientId);
      c.put(WebhookAuthConfig.OAUTH2_CLIENT_SECRET, "secret");
      return c;
    }

    @Override
    public void close() throws IOException {
      socket.close();
      for (Socket s : accepted) s.close();
    }
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

  @Test
  public void testStalledTokenEndpointTimesOut() throws Exception {
    OAuth2TokenCache.timeout = Duration.ofMillis(500);
    stalled = new StalledEndpoint();
    Map<String, String> hung = stalled.config("hung-client");
    long start = System.nanoTime();
    assertThrows(IOException.class, () -> OAuth2TokenCache.getToken(hung));
    assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis() < 3000, is(true));
  }

  @Test
  public void testStalledTokenEndpointDoesNotBlockOtherClients() throws Exception {
    // long enough that the hung fetch is certainly still in flight during the other fetch
    OAuth2TokenCache.timeout = Duration.ofSeconds(5);
    stalled = new StalledEndpoint();
    Map<String, String> hung = stalled.config("hung-client");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<String> blocked = pool.submit(() -> OAuth2TokenCache.getToken(hung));
      Future<String> waiting = pool.submit(() -> OAuth2TokenCache.getToken(hung));
      assertThat(stalled.connections.tryAcquire(2, TimeUnit.SECONDS), is(true));

      // another realm's webhook, with its own token endpoint, is not held up by the hung one
      String token =
          assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OAuth2TokenCache.getToken(config));
      assertThat(token, is("tok-1"));
      assertThat(blocked.isDone(), is(false));

      // both callers for the hung endpoint share one connection and fail once it times out
      assertThrows(ExecutionException.class, () -> blocked.get(10, TimeUnit.SECONDS));
      assertThrows(ExecutionException.class, () -> waiting.get(10, TimeUnit.SECONDS));
      assertThat(stalled.accepted, hasSize(1));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void testConcurrentCallersShareOneFetch() throws Exception {
    int callers = 8;
    // slow enough that every caller arrives while the first fetch is in flight
    delayMillis.set(500);
    ExecutorService pool = Executors.newFixedThreadPool(callers);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < callers; i++) {
        results.add(
            pool.submit(
                () -> {
                  go.await();
                  return OAuth2TokenCache.getToken(config);
                }));
      }
      go.countDown();
      for (Future<String> r : results) assertThat(r.get(10, TimeUnit.SECONDS), is("tok-1"));
      assertThat(server.drain(), hasSize(1));
    } finally {
      pool.shutdownNow();
    }
  }
}
