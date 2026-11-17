package io.phasetwo.keycloak.resources;

import static io.phasetwo.keycloak.Helpers.addEventListener;
import static io.phasetwo.keycloak.Helpers.createUser;
import static io.phasetwo.keycloak.Helpers.removeEventListener;
import static io.phasetwo.keycloak.Helpers.urlencode;
import static io.phasetwo.keycloak.events.format.GoogleSecOpsPayloadFormatterTest.ENVELOPE_FIELDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.phasetwo.keycloak.RecordingServer;
import io.phasetwo.keycloak.RecordingServer.Captured;
import io.phasetwo.keycloak.RecordingServer.Reply;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.broker.provider.util.LegacySimpleHttp;
import org.keycloak.util.JsonSerialization;

/**
 * Keycloak sends real user and admin events to a stand-in for a Google SecOps webhook feed, with
 * the feed's auth headers and the Datastream-shaped records the KEYCLOAK parser reads.
 */
@JBossLog
public class GoogleSecOpsFormatIntegrationTest extends AbstractResourceTest {

  static final Duration WAIT = Duration.ofSeconds(20);
  static final String FEED_PATH =
      "/v1beta/projects/my-project/locations/us/instances/my-instance/feeds/keycloak-feed:importPushLogs";
  static final String API_KEY = "AIzaSyTestApiKey";
  static final String FEED_SECRET = "feed-secret-value";

  CloseableHttpClient httpClient = HttpClients.createDefault();

  String webhooksUrl() {
    return getAuthUrl() + "/realms/master/webhooks";
  }

  static Map<String, Object> record(Captured c) throws Exception {
    return JsonSerialization.readValue(c.body(), new TypeReference<Map<String, Object>>() {});
  }

  static boolean hasType(Captured c, String type) {
    try {
      return type.equals(((Map<?, ?>) record(c).get("payload")).get("type"));
    } catch (Exception e) {
      return false;
    }
  }

  String createWebhook(Set<String> eventTypes) throws Exception {
    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setEnabled(true);
    rep.setUrl("http://host.testcontainers.internal:" + WEBHOOK_SERVER_PORT + FEED_PATH);
    rep.setEventTypes(eventTypes);
    rep.setPayloadFormat("google-secops");
    rep.setAuthType("headers");
    rep.setConfig(
        Map.of("header.x-goog-chronicle-auth", API_KEY, "header.x-chronicle-auth", FEED_SECRET));
    LegacySimpleHttp.Response created =
        LegacySimpleHttp.doPost(webhooksUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(created.getStatus(), is(201));
    String loc = created.getFirstHeader("Location");
    return loc.substring(loc.lastIndexOf('/') + 1);
  }

  void deleteWebhook(String id) throws Exception {
    if (id == null) return;
    LegacySimpleHttp.doDelete(webhooksUrl() + "/" + urlencode(id), httpClient)
        .auth(keycloak.tokenManager().getAccessTokenString())
        .asResponse();
  }

  static void assertFeedRequest(Captured c) {
    assertThat(c.path(), is(FEED_PATH));
    assertThat(c.header("x-goog-chronicle-auth"), is(API_KEY));
    assertThat(c.header("x-chronicle-auth"), is(FEED_SECRET));
    assertThat(c.header("Content-Type"), is("application/json"));
  }

  @Test
  public void testSecOpsWebhookFeed() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer feed =
        new RecordingServer(WEBHOOK_SERVER_PORT)
            .post(FEED_PATH, c -> Reply.json(200, "{}"))
            .start()) {
      id = createWebhook(Set.of("access.LOGIN", "admin.USER-CREATE"));

      Keycloak.getInstance(
              getAuthUrl(),
              "master",
              container.getAdminUsername(),
              container.getAdminPassword(),
              ADMIN_CLI)
          .tokenManager()
          .getAccessTokenString();
      Captured login = feed.await(WAIT, c -> hasType(c, "LOGIN"));
      log.infof("secops LOGIN request body: %s", login.bodyAsString());
      assertFeedRequest(login);
      Map<String, Object> r = record(login);
      assertThat(r.keySet(), is(ENVELOPE_FIELDS));
      assertThat(r.get("object"), is("keycloak_event_entity"));
      assertThat(((Map<?, ?>) r.get("source_metadata")).get("table"), is("event_entity"));
      Instant sourceTime = Instant.parse((String) r.get("source_timestamp"));
      assertThat(Duration.between(sourceTime, Instant.now()).abs().toMinutes() < 5, is(true));
      Map<?, ?> p = (Map<?, ?>) r.get("payload");
      assertThat(p.get("type"), is("LOGIN"));
      assertThat(p.get("client_id"), is(ADMIN_CLI));
      assertThat(p.get("user_id"), notNullValue());
      assertThat(p.get("session_id"), notNullValue());
      assertThat(p.get("ip_address"), notNullValue());
      assertThat(p.get("realm_id"), notNullValue());
      assertThat(p.get("error"), nullValue());
      Map<String, String> details =
          JsonSerialization.readValue(
              (String) p.get("details_json"), new TypeReference<Map<String, String>>() {});
      assertThat(details.get("username"), is(container.getAdminUsername()));
      assertThat(details.get("grant_type"), is("password"));

      createUser(keycloak, "master", "secops-user");
      Captured admin = feed.await(WAIT, c -> hasType(c, "USER-CREATE"));
      log.infof("secops admin request body: %s", admin.bodyAsString());
      assertFeedRequest(admin);
      Map<String, Object> a = record(admin);
      assertThat(a.get("object"), is("keycloak_admin_event_entity"));
      Map<?, ?> ap = (Map<?, ?>) a.get("payload");
      assertThat(ap.get("operation_type"), is("CREATE"));
      assertThat(ap.get("resource_type"), is("USER"));
      assertThat(((String) ap.get("resource_path")).startsWith("users/"), is(true));
      assertThat(ap.get("auth_user_id"), notNullValue());
    } finally {
      deleteWebhook(id);
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  @Test
  public void testThrottledRequestIsRetried() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    AtomicInteger calls = new AtomicInteger();
    try (RecordingServer feed =
        new RecordingServer(WEBHOOK_SERVER_PORT)
            .post(
                FEED_PATH,
                c ->
                    calls.incrementAndGet() == 1
                        ? Reply.json(429, "{\"error\":{\"code\":429}}")
                        : Reply.json(200, "{}"))
            .start()) {
      id = createWebhook(Set.of("admin.USER-CREATE"));
      createUser(keycloak, "master", "secops-retry-user");
      Captured first = feed.await(WAIT, c -> hasType(c, "USER-CREATE"));
      Captured retry = feed.await(WAIT, c -> hasType(c, "USER-CREATE"));
      // the retry is the same record, byte for byte
      assertThat(retry.bodyAsString(), is(first.bodyAsString()));
      assertThat(calls.get(), is(2));
    } finally {
      deleteWebhook(id);
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  @Test
  public void testInvalidRequestIsNotRetried() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer feed =
        new RecordingServer(WEBHOOK_SERVER_PORT)
            .post(FEED_PATH, c -> Reply.json(400, "{\"error\":{\"code\":400}}"))
            .start()) {
      id = createWebhook(Set.of("admin.USER-CREATE"));
      createUser(keycloak, "master", "secops-invalid-user");
      feed.await(WAIT, c -> hasType(c, "USER-CREATE"));
      Thread.sleep(3000);
      assertThat(feed.drain().size(), is(0));
    } finally {
      deleteWebhook(id);
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }
}
