package io.phasetwo.keycloak.resources;

import static io.phasetwo.keycloak.Helpers.addEventListener;
import static io.phasetwo.keycloak.Helpers.createUser;
import static io.phasetwo.keycloak.Helpers.removeEventListener;
import static io.phasetwo.keycloak.Helpers.urlencode;
import static io.phasetwo.keycloak.events.format.AzureSentinelPayloadFormatterTest.assertRowsMatchStream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import io.phasetwo.keycloak.RecordingServer;
import io.phasetwo.keycloak.RecordingServer.Captured;
import io.phasetwo.keycloak.RecordingServer.Reply;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.time.Duration;
import java.util.List;
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
 * Keycloak sends real user and admin events to a stand-in for the Azure Monitor Logs Ingestion API,
 * authenticating with a client-credentials token from a stand-in Entra token endpoint.
 */
@JBossLog
public class AzureSentinelFormatIntegrationTest extends AbstractResourceTest {

  static final Duration WAIT = Duration.ofSeconds(20);
  static final String TENANT = "00000000-0000-0000-0000-000000000001";
  static final String TOKEN_PATH = "/" + TENANT + "/oauth2/v2.0/token";
  static final String STREAM_PATH =
      "/dataCollectionRules/dcr-00000000000000000000000000000000/streams/Custom-KeycloakEvents";
  static final String HOST = "http://host.testcontainers.internal:" + WEBHOOK_SERVER_PORT;

  CloseableHttpClient httpClient = HttpClients.createDefault();
  AtomicInteger tokens = new AtomicInteger();

  String webhooksUrl() {
    return getAuthUrl() + "/realms/master/webhooks";
  }

  Reply token(Captured c) {
    return Reply.json(
        200,
        "{\"token_type\":\"Bearer\",\"expires_in\":3599,\"ext_expires_in\":3599,\"access_token\":\"entra-"
            + tokens.incrementAndGet()
            + "\"}");
  }

  static List<Map<String, Object>> rows(Captured c) throws Exception {
    return JsonSerialization.readValue(c.body(), new TypeReference<List<Map<String, Object>>>() {});
  }

  static boolean hasEventType(Captured c, String type) {
    try {
      return c.path().equals(STREAM_PATH) && type.equals(rows(c).get(0).get("EventType"));
    } catch (Exception e) {
      return false;
    }
  }

  String createWebhook(Set<String> eventTypes) throws Exception {
    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setEnabled(true);
    rep.setUrl(HOST + STREAM_PATH + "?api-version=2023-01-01");
    rep.setEventTypes(eventTypes);
    rep.setPayloadFormat("azure-sentinel");
    rep.setAuthType("oauth2-client-credentials");
    rep.setConfig(
        Map.of(
            "oauth2.tokenUrl", HOST + TOKEN_PATH,
            "oauth2.clientId", "11111111-2222-3333-4444-555555555555",
            "oauth2.clientSecret", "app-registration-secret",
            "oauth2.scope", "https://monitor.azure.com//.default"));
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

  static void assertIngestionRequest(Captured c) {
    assertThat(c.path(), is(STREAM_PATH));
    assertThat(c.query(), is("api-version=2023-01-01"));
    assertThat(c.header("Authorization").startsWith("Bearer entra-"), is(true));
    assertThat(c.header("Content-Type"), is("application/json"));
  }

  @Test
  public void testLogsIngestionApi() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer azure =
        new RecordingServer(WEBHOOK_SERVER_PORT)
            .post(TOKEN_PATH, this::token)
            .post(STREAM_PATH, c -> Reply.status(204))
            .start()) {
      id = createWebhook(Set.of("access.LOGIN", "access.LOGIN_ERROR", "admin.USER-CREATE"));

      Keycloak.getInstance(
              getAuthUrl(),
              "master",
              container.getAdminUsername(),
              container.getAdminPassword(),
              ADMIN_CLI)
          .tokenManager()
          .getAccessTokenString();

      Captured tokenRequest = azure.await(WAIT, c -> c.path().equals(TOKEN_PATH));
      String form = tokenRequest.bodyAsString();
      assertThat(form, containsString("grant_type=client_credentials"));
      assertThat(form, containsString("client_id=11111111-2222-3333-4444-555555555555"));
      assertThat(form, containsString("client_secret=app-registration-secret"));
      assertThat(form, containsString("scope=https%3A%2F%2Fmonitor.azure.com%2F%2F.default"));

      Captured login = azure.await(WAIT, c -> hasEventType(c, "LOGIN"));
      log.infof("sentinel LOGIN request body: %s", login.bodyAsString());
      assertIngestionRequest(login);
      List<Map<String, Object>> rows = rows(login);
      assertRowsMatchStream(rows);
      Map<String, Object> r = rows.get(0);
      assertThat(r.get("EventCategory"), is("user"));
      assertThat(r.get("EventTypeFull"), is("access.LOGIN"));
      assertThat(r.get("EventResult"), is("Success"));
      assertThat(r.get("RealmName"), is("master"));
      assertThat(r.get("ClientId"), is(ADMIN_CLI));
      assertThat(r.get("Username"), is(container.getAdminUsername()));
      assertThat(r.get("UserId"), notNullValue());
      assertThat(r.get("SessionId"), notNullValue());
      assertThat(r.get("IpAddress"), notNullValue());
      assertThat(((Map<?, ?>) r.get("Details")).get("grant_type"), is("password"));

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
      Captured failed = azure.await(WAIT, c -> hasEventType(c, "LOGIN_ERROR"));
      assertIngestionRequest(failed);
      Map<String, Object> fr = rows(failed).get(0);
      assertThat(fr.get("EventResult"), is("Failure"));
      assertThat(fr.get("Error"), is("invalid_user_credentials"));

      createUser(keycloak, "master", "sentinel-user");
      Captured admin = azure.await(WAIT, c -> hasEventType(c, "CREATE"));
      log.infof("sentinel admin request body: %s", admin.bodyAsString());
      assertIngestionRequest(admin);
      List<Map<String, Object>> adminRows = rows(admin);
      assertRowsMatchStream(adminRows);
      Map<String, Object> ar = adminRows.get(0);
      assertThat(ar.get("EventCategory"), is("admin"));
      assertThat(ar.get("EventTypeFull"), is("admin.USER-CREATE"));
      assertThat(ar.get("ResourceType"), is("USER"));
      assertThat(((String) ar.get("ResourcePath")).startsWith("users/"), is(true));
      assertThat(ar.get("Username"), is(container.getAdminUsername()));
      assertThat(ar.get("TargetUsername"), is("sentinel-user"));
      assertThat(ar.get("ResourcePath"), is("users/" + ar.get("TargetUserId")));

      // the token is cached across sends
      assertThat(tokens.get(), is(1));
    } finally {
      deleteWebhook(id);
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  @Test
  public void testExpiredTokenIsRefreshedAndSendRetried() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer azure =
        new RecordingServer(WEBHOOK_SERVER_PORT)
            .post(TOKEN_PATH, this::token)
            // reject the first token as if it had been revoked
            .post(
                STREAM_PATH,
                c ->
                    "Bearer entra-1".equals(c.header("Authorization"))
                        ? Reply.json(401, "{\"error\":{\"code\":\"InvalidToken\"}}")
                        : Reply.status(204))
            .start()) {
      // a fresh client id, so no token is cached from the other test
      id = createWebhookWithClient("66666666-7777-8888-9999-000000000000");
      createUser(keycloak, "master", "sentinel-retry-user");

      Captured rejected = azure.await(WAIT, c -> hasEventType(c, "CREATE"));
      assertThat(rejected.header("Authorization"), is("Bearer entra-1"));
      Captured accepted = azure.await(WAIT, c -> hasEventType(c, "CREATE"));
      assertThat(accepted.header("Authorization"), is("Bearer entra-2"));
      assertThat(accepted.bodyAsString(), is(rejected.bodyAsString()));
    } finally {
      deleteWebhook(id);
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  String createWebhookWithClient(String clientId) throws Exception {
    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setEnabled(true);
    rep.setUrl(HOST + STREAM_PATH + "?api-version=2023-01-01");
    rep.setEventTypes(Set.of("admin.USER-CREATE"));
    rep.setPayloadFormat("azure-sentinel");
    rep.setAuthType("oauth2-client-credentials");
    rep.setConfig(
        Map.of(
            "oauth2.tokenUrl",
            HOST + TOKEN_PATH,
            "oauth2.clientId",
            clientId,
            "oauth2.clientSecret",
            "app-registration-secret",
            "oauth2.scope",
            "https://monitor.azure.com//.default"));
    LegacySimpleHttp.Response created =
        LegacySimpleHttp.doPost(webhooksUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(created.getStatus(), is(201));
    String loc = created.getFirstHeader("Location");
    return loc.substring(loc.lastIndexOf('/') + 1);
  }
}
