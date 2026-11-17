package io.phasetwo.keycloak.resources;

import static io.phasetwo.keycloak.Helpers.addEventListener;
import static io.phasetwo.keycloak.Helpers.createUser;
import static io.phasetwo.keycloak.Helpers.removeEventListener;
import static io.phasetwo.keycloak.Helpers.urlencode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import io.phasetwo.keycloak.RecordingServer;
import io.phasetwo.keycloak.RecordingServer.Captured;
import io.phasetwo.keycloak.RecordingServer.Reply;
import io.phasetwo.keycloak.events.format.DatadogKeycloakPipeline;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.broker.provider.util.LegacySimpleHttp;
import org.keycloak.util.JsonSerialization;

/**
 * Keycloak sends real user and admin events to a stand-in for the Datadog Logs intake API, and the
 * entries are what Datadog's Keycloak integration expects.
 */
@JBossLog
public class DatadogFormatIntegrationTest extends AbstractResourceTest {

  static final Duration WAIT = Duration.ofSeconds(20);

  final DatadogKeycloakPipeline pipeline = new DatadogKeycloakPipeline();
  static final String INTAKE_PATH = "/api/v2/logs";
  static final String API_KEY = "0123456789abcdef0123456789abcdef";

  CloseableHttpClient httpClient = HttpClients.createDefault();

  String webhooksUrl() {
    return getAuthUrl() + "/realms/master/webhooks";
  }

  static List<Map<String, Object>> entries(Captured c) throws Exception {
    return JsonSerialization.readValue(c.body(), new TypeReference<List<Map<String, Object>>>() {});
  }

  static Map<String, Object> single(Captured c) throws Exception {
    List<Map<String, Object>> entries = entries(c);
    assertThat(entries.size(), is(1));
    return entries.get(0);
  }

  static boolean hasEvent(Captured c, String evtName) {
    try {
      Map<?, ?> evt = (Map<?, ?>) single(c).get("evt");
      return evtName.equals(evt.get("name"));
    } catch (Exception e) {
      return false;
    }
  }

  @Test
  public void testDatadogIntake() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer intake =
        new RecordingServer(WEBHOOK_SERVER_PORT)
            .post(INTAKE_PATH, c -> Reply.json(202, "{}"))
            .start()) {
      WebhookRepresentation rep = new WebhookRepresentation();
      rep.setEnabled(true);
      rep.setUrl("http://host.testcontainers.internal:" + WEBHOOK_SERVER_PORT + INTAKE_PATH);
      rep.setEventTypes(Set.of("access.LOGIN", "access.LOGIN_ERROR", "admin.USER-CREATE"));
      rep.setPayloadFormat("datadog");
      rep.setAuthType("headers");
      rep.setConfig(Map.of("header.DD-API-KEY", API_KEY, "tags", "env:test,team:iam"));
      LegacySimpleHttp.Response created =
          LegacySimpleHttp.doPost(webhooksUrl(), httpClient)
              .auth(keycloak.tokenManager().getAccessTokenString())
              .json(rep)
              .asResponse();
      assertThat(created.getStatus(), is(201));
      String loc = created.getFirstHeader("Location");
      id = loc.substring(loc.lastIndexOf('/') + 1);

      // a successful login (user event)
      Keycloak.getInstance(
              getAuthUrl(),
              "master",
              container.getAdminUsername(),
              container.getAdminPassword(),
              ADMIN_CLI)
          .tokenManager()
          .getAccessTokenString();
      Captured login = intake.await(WAIT, c -> hasEvent(c, "LOGIN"));
      assertIntakeRequest(login);
      log.infof("datadog LOGIN request body: %s", login.bodyAsString());
      Map<String, Object> entry = single(login);
      assertThat(entry.get("ddsource"), is("keycloak"));
      assertThat(entry.get("service"), is("keycloak"));
      assertThat(entry.get("ddtags"), is("env:test,team:iam"));
      assertThat(entry.get("status"), is("info"));
      assertThat(entry.get("loggerName"), is("org.keycloak.events"));
      assertThat(entry.get("hostname"), notNullValue());
      String message = (String) entry.get("message");
      assertThat(pipeline.matches(message), is(true));
      Map<String, String> kv = pipeline.attributes(message);
      assertThat(kv.get("type"), is("LOGIN"));
      assertThat(kv.get("realmName"), is("master"));
      assertThat(kv.get("clientId"), is(ADMIN_CLI));
      assertThat(kv.get("username"), is(container.getAdminUsername()));
      assertThat(kv.get("grant_type"), is("password"));
      Map<?, ?> usr = (Map<?, ?>) entry.get("usr");
      assertThat(usr.get("id"), is(kv.get("userId")));
      assertThat(usr.get("name"), is(container.getAdminUsername()));
      assertThat(
          ((Map<?, ?>) ((Map<?, ?>) entry.get("network")).get("client")).get("ip"),
          is(kv.get("ipAddress")));
      assertThat(
          entry.get("evt"),
          is(Map.of("name", "LOGIN", "outcome", "success", "category", "authentication")));

      // a failed login (user event with an error)
      assertThrows(
          Exception.class,
          () ->
              Keycloak.getInstance(
                      getAuthUrl(),
                      "master",
                      container.getAdminUsername(),
                      "wrong-password",
                      ADMIN_CLI)
                  .tokenManager()
                  .getAccessTokenString());
      Captured failed = intake.await(WAIT, c -> hasEvent(c, "LOGIN_ERROR"));
      assertIntakeRequest(failed);
      Map<String, Object> error = single(failed);
      assertThat(error.get("status"), is("warn"));
      assertThat(((Map<?, ?>) error.get("evt")).get("outcome"), is("failure"));
      String errorMessage = (String) error.get("message");
      assertThat(pipeline.matches(errorMessage), is(true));
      assertThat(pipeline.attributes(errorMessage).get("error"), is("invalid_user_credentials"));

      // an admin event
      createUser(keycloak, "master", "datadog-user");
      Captured admin = intake.await(WAIT, c -> hasEvent(c, "CREATE"));
      assertIntakeRequest(admin);
      log.infof("datadog admin request body: %s", admin.bodyAsString());
      Map<String, Object> adminEntry = single(admin);
      String adminMessage = (String) adminEntry.get("message");
      assertThat(pipeline.matches(adminMessage), is(true));
      Map<String, String> akv = pipeline.attributes(adminMessage);
      assertThat(akv.get("operationType"), is("CREATE"));
      assertThat(akv.get("resourceType"), is("USER"));
      assertThat(akv.get("type"), nullValue());
      // the acting admin stays the user; the created user is the resource
      assertThat(akv.get("userId"), is(((Map<?, ?>) adminEntry.get("usr")).get("id")));
      assertThat(akv.get("resourceUsername"), is("datadog-user"));
      assertThat(((Map<?, ?>) adminEntry.get("keycloak")).get("type"), is("admin.USER-CREATE"));
    } finally {
      if (id != null) {
        LegacySimpleHttp.doDelete(webhooksUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
      }
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  @Test
  public void testRejectedApiKeyIsNotRetried() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer intake =
        new RecordingServer(WEBHOOK_SERVER_PORT)
            .post(INTAKE_PATH, c -> Reply.json(403, "{\"errors\":[\"Forbidden\"]}"))
            .start()) {
      WebhookRepresentation rep = new WebhookRepresentation();
      rep.setEnabled(true);
      rep.setUrl("http://host.testcontainers.internal:" + WEBHOOK_SERVER_PORT + INTAKE_PATH);
      rep.setEventTypes(Set.of("admin.USER-CREATE"));
      rep.setPayloadFormat("datadog");
      rep.setAuthType("headers");
      rep.setConfig(Map.of("header.DD-API-KEY", "bad-key"));
      LegacySimpleHttp.Response created =
          LegacySimpleHttp.doPost(webhooksUrl(), httpClient)
              .auth(keycloak.tokenManager().getAccessTokenString())
              .json(rep)
              .asResponse();
      assertThat(created.getStatus(), is(201));
      String loc = created.getFirstHeader("Location");
      id = loc.substring(loc.lastIndexOf('/') + 1);

      createUser(keycloak, "master", "datadog-forbidden-user");
      intake.await(WAIT, c -> true);
      // the default backoff would retry after ~500ms; a 403 must not be retried
      Thread.sleep(3000);
      assertThat(intake.drain().size(), is(0));
    } finally {
      if (id != null) {
        LegacySimpleHttp.doDelete(webhooksUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
      }
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  static void assertIntakeRequest(Captured c) {
    assertThat(c.path(), is(INTAKE_PATH));
    assertThat(c.header("DD-API-KEY"), is(API_KEY));
    assertThat(c.header("Content-Type"), is("application/json"));
    assertThat(c.header("X-Keycloak-Signature"), nullValue());
  }
}
