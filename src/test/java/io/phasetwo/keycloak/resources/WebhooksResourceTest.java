package io.phasetwo.keycloak.resources;

import static io.phasetwo.keycloak.Helpers.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.xgp.http.server.Server;
import com.google.common.collect.ImmutableSet;
import io.phasetwo.keycloak.KeycloakSuite;
import io.phasetwo.keycloak.events.HttpSenderEventListenerProvider;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.ClassRule;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.util.JsonSerialization;

@JBossLog
public class WebhooksResourceTest {

  @ClassRule public static KeycloakSuite server = KeycloakSuite.SERVER;

  CloseableHttpClient httpClient = HttpClients.createDefault();

  String baseUrl() {
    return server.getAuthUrl() + "/realms/master/webhooks";
  }

  String urlencode(String u) {
    try {
      return URLEncoder.encode(u, "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }

  @Test
  public void testAddGetWebhook() throws Exception {
    Keycloak keycloak = server.client();

    String url = "https://example.com/testAddGetWebhook";
    String id = createWebhook(keycloak, httpClient, baseUrl(), url, "A3jt6D8lz", null);

    SimpleHttp.Response response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(200));
    WebhookRepresentation rep = response.asJson(new TypeReference<WebhookRepresentation>() {});
    assertNotNull(rep);
    assertTrue(rep.isEnabled());
    assertNotNull(rep.getId());
    assertNotNull(rep.getCreatedAt());
    assertNotNull(rep.getCreatedBy());
    assertThat(rep.getRealm(), is("master"));
    assertThat(rep.getUrl(), is(url));
    assertNull(rep.getSecret());

    response =
        SimpleHttp.doDelete(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void testUpdateGetWebhook() throws Exception {
    Keycloak keycloak = server.client();

    String url = "https://example.com/testUpdateGetWebhook";
    String secret = "A3jt6D8lz";
    String id = createWebhook(keycloak, httpClient, baseUrl(), url, secret, null);

    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setUrl(url + "/disabled");
    rep.setEnabled(false);
    rep.setSecret(secret);
    rep.setEventTypes(ImmutableSet.of("*"));

    SimpleHttp.Response response =
        SimpleHttp.doPut(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(204));

    response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(200));
    rep = response.asJson(new TypeReference<WebhookRepresentation>() {});
    assertNotNull(rep);
    assertFalse(rep.isEnabled());
    assertNotNull(rep.getId());
    assertNotNull(rep.getCreatedAt());
    assertNotNull(rep.getCreatedBy());
    assertThat(rep.getRealm(), is("master"));
    assertThat(rep.getUrl(), is(url + "/disabled"));
    assertNull(rep.getSecret());

    response =
        SimpleHttp.doDelete(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void testRemoveWebhoook() throws Exception {
    Keycloak keycloak = server.client();

    String id =
        createWebhook(
            keycloak,
            httpClient,
            baseUrl(),
            "https://en6fowyrouz6q4o.m.pipedream.net",
            "A3jt6D8lz",
            null);

    SimpleHttp.Response response =
        SimpleHttp.doDelete(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(204));

    response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void testWebhookReceivesEvent() throws Exception {
    Keycloak keycloak = server.client();
    // update a realm with the ext-event-webhook listener
    addEventListener(keycloak, "master", "ext-event-webhook");

    AtomicInteger cnt = new AtomicInteger(0);
    AtomicReference<String> body = new AtomicReference<String>();
    AtomicReference<String> shaHeader = new AtomicReference<String>();
    // create a server on a free port with a handler to listen for the event
    int port = nextFreePort(8083, 10000);

    Server server = new Server(port);
    server
        .router()
        .POST(
            "/webhook",
            (request, response) -> {
              String b = request.body();
              log.infof("body %s", b);
              if (b != null && b.contains("events/config")) {
                // skip realm update event
              } else if (cnt.get() == 0) {
                response.body("INTERNAL SERVER ERROR");
                response.status(500);
                cnt.incrementAndGet();
              } else {
                body.set(b);
                shaHeader.set(request.header("X-Keycloak-Signature"));
                response.body("OK");
                response.status(202);
              }
            });
    server.start();
    Thread.sleep(1000l);

    String id =
        createWebhook(
            keycloak,
            httpClient,
            baseUrl(),
            "http://127.0.0.1:" + port + "/webhook",
            "qlfwemke",
            ImmutableSet.of("admin.*"));

    // cause an event to be sent
    createUser(keycloak, "master", "abc123");

    Thread.sleep(2500l);

    // check the handler for the event, after a delay
    String webhookPaylod = body.get();
    assertNotNull(webhookPaylod);
    assertThat(cnt.get(), is(1));
    assertThat(body.get(), containsString("abc123"));
    Map ev = JsonSerialization.readValue(webhookPaylod, Map.class);
    assertThat(ev.get("resourceType"), is("USER"));

    // check hmac
    String sha =
        HttpSenderEventListenerProvider.calculateHmacSha(body.get(), "qlfwemke", "HmacSHA256");
    log.infof("hmac header %s sha %s", shaHeader.get(), sha);
    assertThat(shaHeader.get(), is(sha));

    // cause a custom event to be send
    createOrg(keycloak, "foo");

    Thread.sleep(2500l);

    // check the handler for the event, after a delay
    webhookPaylod = body.get();
    assertNotNull(webhookPaylod);
    assertThat(body.get(), containsString("foo"));
    ev = JsonSerialization.readValue(webhookPaylod, Map.class);
    assertThat(ev.get("resourceType"), is("ORGANIZATION"));

    // remove the event listener
    removeEventListener(keycloak, "master", "ext-event-webhook");

    server.stop();

    SimpleHttp.Response response =
        SimpleHttp.doDelete(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(204));
  }

  void createOrg(Keycloak keycloak, String name) throws Exception {
    Map<String, String> m = new HashMap<>();
    m.put("name", name);
    m.put("realm", "master");

    SimpleHttp.Response response =
        SimpleHttp.doPost(server.getAuthUrl() + "/realms/master/orgs", httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(m)
            .asResponse();
    assertThat(response.getStatus(), is(201));
  }
}
