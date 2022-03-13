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
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.net.URLEncoder;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.ClassRule;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.broker.provider.util.SimpleHttp;

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

    AtomicReference<String> body = new AtomicReference<String>();
    // create a server on a free port with a handler to listen for the event
    int port = nextFreePort(8083, 10000);
    String id =
        createWebhook(
            keycloak,
            httpClient,
            baseUrl(),
            "http://127.0.0.1:" + port + "/webhook",
            "qlfwemke",
            ImmutableSet.of("admin.*"));

    Server server = new Server(port);
    server
        .router()
        .POST(
            "/webhook",
            (request, response) -> {
              String r = request.body();
              log.infof("%s", r);
              body.set(r);
              response.body("OK");
              response.status(202);
            });
    server.start();
    Thread.sleep(1000l);

    // cause an event to be sent
    createUser(keycloak, "master", "abc123");

    Thread.sleep(1000l);

    // check the handler for the event, after a delay
    assertNotNull(body.get());
    assertThat(body.get(), containsString("abc123"));

    server.stop();

    SimpleHttp.Response response =
        SimpleHttp.doDelete(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(204));
  }
}
