package io.phasetwo.keycloak.resources;

import static io.phasetwo.keycloak.Helpers.addEventListener;
import static io.phasetwo.keycloak.Helpers.createUser;
import static io.phasetwo.keycloak.Helpers.removeEventListener;
import static io.phasetwo.keycloak.Helpers.urlencode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.phasetwo.keycloak.RecordingServer;
import io.phasetwo.keycloak.RecordingServer.Captured;
import io.phasetwo.keycloak.RecordingServer.Reply;
import io.phasetwo.keycloak.events.HttpSenderEventListenerProvider;
import io.phasetwo.keycloak.representation.RealmAttributeRepresentation;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.keycloak.broker.provider.util.LegacySimpleHttp;
import org.keycloak.util.JsonSerialization;

/** Per-webhook payload format and config, and the config-driven auth types, end to end. */
public class WebhookFormatConfigTest extends AbstractResourceTest {

  static final Duration WAIT = Duration.ofSeconds(20);
  static final String HOST_URL = "http://host.testcontainers.internal:" + WEBHOOK_SERVER_PORT;

  CloseableHttpClient httpClient = HttpClients.createDefault();

  String baseUrl() {
    return getAuthUrl() + "/realms/master/webhooks";
  }

  String token() {
    return keycloak.tokenManager().getAccessTokenString();
  }

  LegacySimpleHttp.Response post(WebhookRepresentation rep) throws Exception {
    return LegacySimpleHttp.doPost(baseUrl(), httpClient).auth(token()).json(rep).asResponse();
  }

  String create(WebhookRepresentation rep) throws Exception {
    LegacySimpleHttp.Response response = post(rep);
    assertThat(response.getStatus(), is(201));
    String loc = response.getFirstHeader("Location");
    return loc.substring(loc.lastIndexOf("/") + 1);
  }

  WebhookRepresentation get(String id) throws Exception {
    return LegacySimpleHttp.doGet(baseUrl() + "/" + urlencode(id), httpClient)
        .auth(token())
        .asJson(new TypeReference<WebhookRepresentation>() {});
  }

  void delete(String id) throws Exception {
    LegacySimpleHttp.doDelete(baseUrl() + "/" + urlencode(id), httpClient)
        .auth(token())
        .asResponse();
  }

  static WebhookRepresentation webhook(String url) {
    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setEnabled(true);
    rep.setUrl(url);
    rep.setEventTypes(Set.of("admin.USER-CREATE"));
    return rep;
  }

  static boolean isUserCreate(Captured c) {
    return c.path().equals("/webhook");
  }

  @Test
  public void testFormatsEndpointListsDefault() throws Exception {
    List<Map<String, Object>> formats =
        LegacySimpleHttp.doGet(baseUrl() + "/formats", httpClient)
            .auth(token())
            .asJson(new TypeReference<List<Map<String, Object>>>() {});
    assertThat(formats.stream().map(f -> f.get("id")).toList(), hasItem("default"));
  }

  @Test
  public void testUnknownFormatIsRejected() throws Exception {
    WebhookRepresentation rep = webhook("https://example.com/unknown-format");
    rep.setPayloadFormat("no-such-format");
    LegacySimpleHttp.Response response = post(rep);
    assertThat(response.getStatus(), is(400));
    assertThat(response.asString(), containsString("no-such-format"));
  }

  @Test
  public void testHeadersAuthRequiresAHeader() throws Exception {
    WebhookRepresentation rep = webhook("https://example.com/headers");
    rep.setAuthType("headers");
    assertThat(post(rep).getStatus(), is(400));
  }

  @Test
  public void testOAuth2RequiresClientCredentials() throws Exception {
    WebhookRepresentation rep = webhook("https://example.com/oauth2");
    rep.setAuthType("oauth2-client-credentials");
    rep.setConfig(Map.of("oauth2.tokenUrl", "https://login.example.com/token"));
    assertThat(post(rep).getStatus(), is(400));
  }

  @Test
  public void testExplicitDefaultFormatIsUnchangedAndSigned() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer server =
        new RecordingServer(WEBHOOK_SERVER_PORT).post("/webhook", c -> Reply.status(200)).start()) {
      WebhookRepresentation rep = webhook(HOST_URL + "/webhook");
      rep.setPayloadFormat("default");
      rep.setSecret("default-secret");
      id = create(rep);
      assertThat(get(id).getPayloadFormat(), is("default"));

      createUser(keycloak, "master", "fmt-default-user");
      Captured c = server.await(WAIT, WebhookFormatConfigTest::isUserCreate);
      Map<?, ?> ev = JsonSerialization.readValue(c.body(), Map.class);
      assertThat(ev.get("type"), is("admin.USER-CREATE"));
      assertThat(ev.get("resourceType"), is("USER"));
      assertThat(c.header("Content-Type"), is("application/json"));
      assertThat(
          c.header("X-Keycloak-Signature"),
          is(
              HttpSenderEventListenerProvider.calculateHmacSha(
                  c.body(), "default-secret", "HmacSHA256")));
    } finally {
      if (id != null) delete(id);
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  @Test
  public void testHeadersAuthAndSecretMasking() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer server =
        new RecordingServer(WEBHOOK_SERVER_PORT).post("/webhook", c -> Reply.status(202)).start()) {
      WebhookRepresentation rep = webhook(HOST_URL + "/webhook");
      rep.setAuthType("headers");
      rep.setConfig(Map.of("header.X-Api-Key", "k3y-v4lue", "note", "visible"));
      id = create(rep);

      // secret header values are masked, other config is returned
      WebhookRepresentation got = get(id);
      assertThat(got.getAuthType(), is("headers"));
      assertThat(got.getConfig().get("header.X-Api-Key"), is("**********"));
      assertThat(got.getConfig().get("note"), is("visible"));

      // sending the masked representation back keeps the stored secret
      got.setConfig(new HashMap<>(got.getConfig()));
      got.getConfig().put("note", "changed");
      LegacySimpleHttp.Response put =
          LegacySimpleHttp.doPut(baseUrl() + "/" + urlencode(id), httpClient)
              .auth(token())
              .json(got)
              .asResponse();
      assertThat(put.getStatus(), is(204));
      assertThat(get(id).getConfig().get("note"), is("changed"));

      // one update that removes, adds and changes entries
      WebhookRepresentation again = get(id);
      again.setConfig(Map.of("header.X-Api-Key", "**********", "other", "added"));
      put =
          LegacySimpleHttp.doPut(baseUrl() + "/" + urlencode(id), httpClient)
              .auth(token())
              .json(again)
              .asResponse();
      assertThat(put.getStatus(), is(204));
      assertThat(
          get(id).getConfig(), is(Map.of("header.X-Api-Key", "**********", "other", "added")));
      again.setConfig(Map.of("header.X-Api-Key", "k3y-v4lue", "other", "added"));
      put =
          LegacySimpleHttp.doPut(baseUrl() + "/" + urlencode(id), httpClient)
              .auth(token())
              .json(again)
              .asResponse();
      assertThat(put.getStatus(), is(204));

      createUser(keycloak, "master", "fmt-headers-user");
      Captured c = server.await(WAIT, WebhookFormatConfigTest::isUserCreate);
      assertThat(c.header("X-Api-Key"), is("k3y-v4lue"));
      assertThat(c.header("X-Keycloak-Signature"), nullValue());
      assertThat(c.bodyAsString(), containsString("fmt-headers-user"));
    } finally {
      if (id != null) delete(id);
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  @Test
  public void testOAuth2ClientCredentialsAuth() throws Exception {
    addEventListener(keycloak, "master", "ext-event-webhook");
    String id = null;
    try (RecordingServer server =
        new RecordingServer(WEBHOOK_SERVER_PORT)
            .post(
                "/token",
                c ->
                    Reply.json(
                        200,
                        "{\"access_token\":\"entra-token-1\",\"token_type\":\"Bearer\",\"expires_in\":3599}"))
            .post("/webhook", c -> Reply.status(204))
            .start()) {
      WebhookRepresentation rep = webhook(HOST_URL + "/webhook");
      rep.setAuthType("oauth2-client-credentials");
      rep.setConfig(
          Map.of(
              "oauth2.tokenUrl", HOST_URL + "/token",
              "oauth2.clientId", "my-client",
              "oauth2.clientSecret", "my-client-secret",
              "oauth2.scope", "https://monitor.azure.com//.default"));
      id = create(rep);
      assertThat(get(id).getConfig().get("oauth2.clientSecret"), is("**********"));
      assertThat(get(id).getConfig().get("oauth2.clientId"), is("my-client"));

      createUser(keycloak, "master", "fmt-oauth2-user");
      Captured tokenRequest = server.await(WAIT, c -> c.path().equals("/token"));
      assertThat(tokenRequest.bodyAsString(), containsString("grant_type=client_credentials"));
      assertThat(tokenRequest.bodyAsString(), containsString("client_secret=my-client-secret"));
      Captured c = server.await(WAIT, WebhookFormatConfigTest::isUserCreate);
      assertThat(c.header("Authorization"), is("Bearer entra-token-1"));
    } finally {
      if (id != null) delete(id);
      removeEventListener(keycloak, "master", "ext-event-webhook");
    }
  }

  @Test
  public void testHttpListenerFormatAndHeaders() throws Exception {
    String key = "_providerConfig.ext-event-http.fmt";
    String attributesUrl = getAuthUrl() + "/realms/master/attributes";
    try (RecordingServer server =
        new RecordingServer(WEBHOOK_SERVER_PORT).post("/webhook", c -> Reply.status(202)).start()) {
      Map<String, Object> config = new HashMap<>();
      config.put("targetUri", HOST_URL + "/webhook");
      config.put("retry", false);
      config.put("format", "default");
      config.put("headers", Map.of("X-Api-Key", "http-key"));
      RealmAttributeRepresentation attr = new RealmAttributeRepresentation();
      attr.setRealm("master");
      attr.setName(key);
      attr.setValue(JsonSerialization.writeValueAsString(config));
      assertThat(
          LegacySimpleHttp.doPost(attributesUrl, httpClient)
              .auth(token())
              .json(attr)
              .asResponse()
              .getStatus(),
          is(201));
      addEventListener(keycloak, "master", "ext-event-http");

      createUser(keycloak, "master", "fmt-http-user");
      Captured c =
          server.await(
              WAIT, r -> r.bodyAsString().contains("fmt-http-user") && r.path().equals("/webhook"));
      assertThat(c.header("X-Api-Key"), is("http-key"));
      // with a format configured the listener sends the unified webhook event shape
      Map<?, ?> ev = JsonSerialization.readValue(c.body(), Map.class);
      assertThat(ev.get("type"), is("admin.USER-CREATE"));
    } finally {
      removeEventListener(keycloak, "master", "ext-event-http");
      LegacySimpleHttp.doDelete(attributesUrl + "/" + urlencode(key), httpClient)
          .auth(token())
          .asResponse();
    }
  }
}
