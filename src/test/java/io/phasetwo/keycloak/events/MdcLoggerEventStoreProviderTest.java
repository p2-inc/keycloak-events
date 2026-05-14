package io.phasetwo.keycloak.events;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import lombok.extern.jbosslog.JBossLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.output.OutputFrame;

/**
 * Boots a Keycloak container configured to use {@link MdcLoggerEventStoreProvider} as its event
 * store and JSON console log output, then asserts that real user/admin actions produce the expected
 * JSON log lines on stdout with the flattened event fields in the {@code mdc.event.*} subtree.
 */
@JBossLog
public class MdcLoggerEventStoreProviderTest {

  static final String KEYCLOAK_IMAGE =
      String.format(
          "quay.io/phasetwo/keycloak-crdb:%s", System.getProperty("keycloak-version", "26.5.7"));
  static final String REALM = "master";
  static final String ADMIN_CLI = "admin-cli";
  static final String PROVIDER_ID = MdcLoggerEventStoreProviderFactory.PROVIDER_ID;
  static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);
  static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  static final JsonLogCapture capture = new JsonLogCapture();
  static final KeycloakContainer container = initContainer();
  static Keycloak keycloak;

  private static KeycloakContainer initContainer() {
    // TLS is enabled because Keycloak 26.5.x's master realm rejects HTTP requests to the
    // token endpoint (sslRequired=EXTERNAL evaluated against the docker bridge IP). No extra
    // provider libs are needed: the provider only depends on classes already on Keycloak's
    // classpath (JBoss logging, Jackson).
    return new KeycloakContainer(KEYCLOAK_IMAGE)
        .withContextPath("/auth")
        .withReuse(false)
        .useTls()
        .withProviderClassesFrom("target/classes")
        .withCustomCommand("--spi-events-store-provider=" + PROVIDER_ID)
        .withCustomCommand("--spi-events-store-" + PROVIDER_ID + "-use-jpa=true")
        .withCustomCommand("--log-console-output=json")
        .withAccessToHost(true)
        .withLogConsumer(capture);
  }

  @BeforeAll
  public static void beforeAll() {
    container.start();
    keycloak = container.getKeycloakAdminClient();
    enableAllEvents(keycloak.realm(REALM));
  }

  @AfterAll
  public static void afterAll() {
    if (container.getContainerId() != null) {
      container.stop();
    }
  }

  private static void enableAllEvents(RealmResource realm) {
    RealmEventsConfigRepresentation events = realm.getRealmEventsConfig();
    events.setEventsEnabled(true);
    events.setAdminEventsEnabled(true);
    events.setAdminEventsDetailsEnabled(true);
    realm.updateRealmEventsConfig(events);
  }

  @Test
  public void adminEventProducesAdminLoggerLine() {
    int before = capture.adminEventCount();

    UserRepresentation user = new UserRepresentation();
    user.setUsername("mdc-admin-event-" + System.currentTimeMillis());
    user.setEnabled(true);
    try (Response resp = keycloak.realm(REALM).users().create(user)) {
      assertThat(resp.getStatus(), is(201));
    }

    JsonNode line =
        await(
            "ADMIN_EVENT_LOGGER line with operationType=CREATE",
            () ->
                capture.forLogger(MdcLoggerEventListenerProvider.ADMIN_EVENT_LOGGER_NAME).stream()
                    .skip(before)
                    .filter(
                        n ->
                            "CREATE".equals(n.at("/mdc/event.operationType").asText())
                                && "USER".equals(n.at("/mdc/event.resourceType").asText()))
                    .findFirst());

    assertThat(line.path("level").asText(), equalTo("INFO"));
    assertThat(line.path("message").asText(), equalTo(MdcLoggerEventListenerProvider.LOG_MESSAGE));
    assertThat(
        line.path("loggerName").asText(),
        equalTo(MdcLoggerEventListenerProvider.ADMIN_EVENT_LOGGER_NAME));

    JsonNode mdc = line.path("mdc");
    assertThat(mdc.path("event.class").asText(), equalTo(FlatAdminEvent.CLASS));
    assertThat(mdc.path("event.operationType").asText(), equalTo("CREATE"));
    assertThat(mdc.path("event.resourceType").asText(), equalTo("USER"));
    assertThat(mdc.path("event.resourcePath").asText(), startsWith("users/"));
    assertThat(mdc.path("event.authRealmName").asText(), equalTo(REALM));
    assertThat(mdc.path("event.authClientId").asText(), is(notNullValue()));
    assertThat(mdc.path("event.id").asText(), matchesPattern("[0-9a-f-]{36}"));
  }

  @Test
  public void userEventProducesEventLoggerLine() throws Exception {
    int before = capture.userEventCount();
    triggerLoginError("nope-no-user", "nope-bad-pass");

    JsonNode line =
        await(
            "EVENT_LOGGER line with type=LOGIN_ERROR",
            () ->
                capture.forLogger(MdcLoggerEventListenerProvider.EVENT_LOGGER_NAME).stream()
                    .skip(before)
                    .filter(n -> "LOGIN_ERROR".equals(n.at("/mdc/event.type").asText()))
                    .findFirst());

    JsonNode mdc = line.path("mdc");
    assertThat(mdc.path("event.class").asText(), equalTo(FlatEvent.CLASS));
    assertThat(mdc.path("event.type").asText(), equalTo("LOGIN_ERROR"));
    assertThat(mdc.path("event.clientId").asText(), equalTo(ADMIN_CLI));
    assertThat(mdc.path("event.realmId").asText(), is(notNullValue()));
    assertThat(mdc.path("event.id").asText(), matchesPattern("[0-9a-f-]{36}"));
  }

  @Test
  public void detailsAreSerializedAsJsonString() throws Exception {
    int before = capture.userEventCount();
    triggerLoginError("details-test-user", "wrong-pass");

    JsonNode line =
        await(
            "EVENT_LOGGER line for LOGIN_ERROR with details",
            () ->
                capture.forLogger(MdcLoggerEventListenerProvider.EVENT_LOGGER_NAME).stream()
                    .skip(before)
                    .filter(n -> "LOGIN_ERROR".equals(n.at("/mdc/event.type").asText()))
                    .filter(n -> !n.at("/mdc/event.detailsJson").isMissingNode())
                    .findFirst());

    String detailsJson = line.path("mdc").path("event.detailsJson").asText();
    JsonNode details = new ObjectMapper().readTree(detailsJson);
    assertThat(details.isObject(), is(true));
  }

  /**
   * Trigger a LOGIN_ERROR by hitting the token endpoint with bad credentials. Bypasses cert
   * validation since the container uses a self-signed TLS cert.
   */
  private static void triggerLoginError(String username, String password) throws Exception {
    javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
    ctx.init(
        null,
        new javax.net.ssl.TrustManager[] {
          new javax.net.ssl.X509TrustManager() {
            public void checkClientTrusted(
                java.security.cert.X509Certificate[] chain, String authType) {}

            public void checkServerTrusted(
                java.security.cert.X509Certificate[] chain, String authType) {}

            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
              return new java.security.cert.X509Certificate[0];
            }
          }
        },
        new java.security.SecureRandom());
    java.net.URL url =
        new java.net.URL(
            container.getAuthServerUrl() + "/realms/master/protocol/openid-connect/token");
    javax.net.ssl.HttpsURLConnection con = (javax.net.ssl.HttpsURLConnection) url.openConnection();
    con.setSSLSocketFactory(ctx.getSocketFactory());
    con.setHostnameVerifier((h, s) -> true);
    con.setRequestMethod("POST");
    con.setDoOutput(true);
    con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    String body =
        "grant_type=password&client_id="
            + ADMIN_CLI
            + "&username="
            + java.net.URLEncoder.encode(username, "UTF-8")
            + "&password="
            + java.net.URLEncoder.encode(password, "UTF-8");
    con.getOutputStream().write(body.getBytes());
    con.getResponseCode(); // we don't care about the result, only the side effect (LOGIN_ERROR)
    con.disconnect();
  }

  @Test
  public void adminEventsTabReturnsJpaResults() {
    UserRepresentation user = new UserRepresentation();
    user.setUsername("query-check-" + System.currentTimeMillis());
    user.setEnabled(true);
    try (Response resp = keycloak.realm(REALM).users().create(user)) {
      assertThat(resp.getStatus(), is(201));
    }

    // With useJpa=true, createAdminQuery/createQuery delegate to JpaEventStoreProvider so the
    // admin events tab returns the events written by the dual-write path.
    await(
        "admin events query to include the CREATE USER event",
        () ->
            keycloak.realm(REALM).getAdminEvents().stream()
                .filter(e -> "CREATE".equals(e.getOperationType()))
                .filter(
                    e -> e.getResourcePath() != null && e.getResourcePath().startsWith("users/"))
                .findFirst());
  }

  @Test
  public void otherKeycloakLogsDoNotInheritEventMdc() {
    int before = capture.adminEventCount();

    UserRepresentation user = new UserRepresentation();
    user.setUsername("leak-check-" + System.currentTimeMillis());
    user.setEnabled(true);
    try (Response resp = keycloak.realm(REALM).users().create(user)) {
      assertThat(resp.getStatus(), is(201));
    }

    // wait for at least one new admin-event log line so we know post-event lines have flushed
    await(
        "admin event after leak-check user create",
        () -> Optional.of(capture.adminEventCount()).filter(c -> c > before));

    long nonEventLinesWithEventMdc =
        capture.allLines().stream()
            .filter(
                n -> {
                  String name = n.path("loggerName").asText("");
                  return !MdcLoggerEventListenerProvider.EVENT_LOGGER_NAME.equals(name)
                      && !MdcLoggerEventListenerProvider.ADMIN_EVENT_LOGGER_NAME.equals(name);
                })
            .filter(
                n -> {
                  JsonNode mdc = n.path("mdc");
                  if (!mdc.isObject()) return false;
                  return mdc.fieldNames().hasNext()
                      && fieldNamesAsList(mdc).stream().anyMatch(k -> k.startsWith("event."));
                })
            .count();

    assertThat(nonEventLinesWithEventMdc, equalTo(0L));
  }

  private static List<String> fieldNamesAsList(JsonNode obj) {
    List<String> names = new ArrayList<>();
    obj.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static <T> T await(String description, Supplier<Optional<T>> producer) {
    long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      Optional<T> v = producer.get();
      if (v.isPresent()) return v.get();
      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while awaiting " + description);
      }
    }
    throw new AssertionError("Timed out after " + AWAIT_TIMEOUT + " awaiting " + description);
  }

  /** Reassembles container stdout into whole JSON lines and parses each one. */
  static final class JsonLogCapture implements java.util.function.Consumer<OutputFrame> {
    private final List<JsonNode> lines = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final StringBuilder buffer = new StringBuilder();

    @Override
    public synchronized void accept(OutputFrame frame) {
      if (frame.getType() != OutputFrame.OutputType.STDOUT) return;
      String chunk = frame.getUtf8String();
      if (chunk == null || chunk.isEmpty()) return;
      buffer.append(chunk);
      int newlineIdx;
      while ((newlineIdx = buffer.indexOf("\n")) >= 0) {
        String line = buffer.substring(0, newlineIdx);
        buffer.delete(0, newlineIdx + 1);
        tryParse(line);
      }
    }

    private void tryParse(String raw) {
      String trimmed = raw.strip();
      if (trimmed.isEmpty() || trimmed.charAt(0) != '{') return;
      try {
        lines.add(mapper.readTree(trimmed));
      } catch (Exception ignored) {
        // not JSON; skip
      }
    }

    List<JsonNode> allLines() {
      return new ArrayList<>(lines);
    }

    List<JsonNode> forLogger(String name) {
      List<JsonNode> result = new ArrayList<>();
      for (JsonNode n : lines) {
        if (name.equals(n.path("loggerName").asText())) result.add(n);
      }
      return result;
    }

    int userEventCount() {
      return forLogger(MdcLoggerEventListenerProvider.EVENT_LOGGER_NAME).size();
    }

    int adminEventCount() {
      return forLogger(MdcLoggerEventListenerProvider.ADMIN_EVENT_LOGGER_NAME).size();
    }
  }
}
