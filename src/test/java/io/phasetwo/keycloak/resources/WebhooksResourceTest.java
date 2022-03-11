package io.phasetwo.keycloak.resources;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableSet;
import io.phasetwo.keycloak.KeycloakSuite;
import io.phasetwo.keycloak.representation.WebhookRepresentation;
import java.net.URLEncoder;
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

    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setEnabled(true);
    rep.setUrl(url);
    rep.setSecret("A3jt6D8lz");
    rep.setEventTypes(ImmutableSet.of("*"));

    SimpleHttp.Response response =
        SimpleHttp.doPost(baseUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(201));
    assertNotNull(response.getFirstHeader("Location"));
    String loc = response.getFirstHeader("Location");
    String id = loc.substring(loc.lastIndexOf("/") + 1);

    log.infof("webhook created at %s with id %s", loc, id);

    response =
        SimpleHttp.doGet(baseUrl() + "/" + urlencode(id), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .asResponse();
    assertThat(response.getStatus(), is(200));
    rep = response.asJson(new TypeReference<WebhookRepresentation>() {});
    assertNotNull(rep);
    assertTrue(rep.isEnabled());
    assertNotNull(rep.getId());
    assertNotNull(rep.getCreatedAt());
    assertNotNull(rep.getCreatedBy());
    assertThat(rep.getRealm(), is("master"));
    assertThat(rep.getUrl(), is(url));
    assertNull(rep.getSecret());
  }

  @Test
  public void testUpdateGetWebhook() throws Exception {
    Keycloak keycloak = server.client();

    String url = "https://example.com/testUpdateGetWebhook";

    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setEnabled(true);
    rep.setUrl(url);
    rep.setSecret("A3jt6D8lz");
    rep.setEventTypes(ImmutableSet.of("*"));

    SimpleHttp.Response response =
        SimpleHttp.doPost(baseUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(201));
    assertNotNull(response.getFirstHeader("Location"));
    String loc = response.getFirstHeader("Location");
    String id = loc.substring(loc.lastIndexOf("/") + 1);

    log.infof("webhook created at %s with id %s", loc, id);

    rep.setUrl(url + "/disabled");
    rep.setEnabled(false);
    response =
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
  }

  @Test
  public void testRemoveAttribute() throws Exception {
    Keycloak keycloak = server.client();

    WebhookRepresentation rep = new WebhookRepresentation();
    rep.setEnabled(true);
    rep.setUrl("https://en6fowyrouz6q4o.m.pipedream.net");
    rep.setSecret("A3jt6D8lz");
    rep.setEventTypes(ImmutableSet.of("*"));

    SimpleHttp.Response response =
        SimpleHttp.doPost(baseUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(201));
    assertNotNull(response.getFirstHeader("Location"));
    String loc = response.getFirstHeader("Location");
    String id = loc.substring(loc.lastIndexOf("/") + 1);

    response =
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
}
