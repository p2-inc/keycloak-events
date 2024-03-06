package io.phasetwo.keycloak.resources;

import static io.phasetwo.keycloak.Helpers.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import com.github.xgp.http.server.Server;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.phasetwo.keycloak.representation.RealmAttributeRepresentation;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.broker.provider.util.SimpleHttp;

@JBossLog
public class EventsResourceTest extends AbstractResourceTest {

  CloseableHttpClient httpClient = HttpClients.createDefault();

  String webhookUrl() {
    return getAuthUrl() + "/realms/master/webhooks";
  }

  String eventsUrl() {
    return getAuthUrl() + "/realms/master/events";
  }

  String attributesUrl() {
    return getAuthUrl() + "/realms/master/attributes";
  }

  @Test
  public void testWebhookReceivesEvent() throws Exception {
    // update a realm with the ext-event-webhook listener
    addEventListener(keycloak, "master", "ext-event-webhook");

    AtomicReference<String> body = new AtomicReference<String>();
    // create a server on a free port with a handler to listen for the event
    int port = WEBHOOK_SERVER_PORT;
    createWebhook(
        keycloak,
        httpClient,
        webhookUrl(),
        "http://host.testcontainers.internal:" + port + "/webhook",
        "qlfwemke",
        ImmutableSet.of("admin.*", "foo.*"));

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
    Map<String, String> ev = ImmutableMap.of("type", "foo.BAR");
    sendEvent(keycloak, ev);

    Thread.sleep(1000l);

    // check the handler for the event, after a delay
    assertNotNull(body.get());
    assertThat(body.get(), containsString("foo.BAR"));

    removeEventListener(keycloak, "master", "ext-event-webhook");
    server.stop();
  }

  @Test
  public void testHttpConfiguredEvent() throws Exception {
    AtomicInteger cnt = new AtomicInteger(0);
    AtomicReference<String> body = new AtomicReference<String>();
    // create a server on a free port with a handler to listen for the event
    int port = WEBHOOK_SERVER_PORT;
    Server server = new Server(port);
    server
        .router()
        .POST(
            "/webhook",
            (request, response) -> {
              log.infof("TEST SERVER %s %s", request.method(), request.body());
              if (cnt.get() == 0) {
                response.body("INTERNAL SERVER ERROR");
                response.status(500);
                cnt.incrementAndGet();
              } else {
                String r = request.body();
                log.infof("body %s", r);
                body.set(r);
                response.body("OK");
                response.status(202);
              }
            });
    server.start();
    Thread.sleep(1000l);

    String targetUri = "http://host.testcontainers.internal:" + port + "/webhook";

    // create the config for a http event listener
    String key = "_providerConfig.ext-event-http.0";
    String value = "{ \"targetUri\": \"" + targetUri + "\", \"retry\": false }";
    RealmAttributeRepresentation rep = new RealmAttributeRepresentation();
    rep.setRealm("master");
    rep.setName(key);
    rep.setValue(value);
    SimpleHttp.Response resp =
        SimpleHttp.doPost(attributesUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(resp.getStatus(), is(201));

    // update a realm with the ext-event-http listener
    addEventListener(keycloak, "master", "ext-event-http");

    // cause an event to be sent
    Map<String, String> ev = ImmutableMap.of("type", "foo.BAR");
    sendEvent(keycloak, ev);

    // wait
    Thread.sleep(1000l);

    // check the handler for the event, after a delay
    assertThat(cnt.get(), is(1));
    assertThat(body.get(), isEmptyOrNullString());

    /*
    // retry = true
    cnt.set(0);
    value = "{ \"targetUri\": \""+targetUri+"\", \"retry\": true }";
    rep.setValue(value);
    resp =
        SimpleHttp.doPut(attributesUrl() + "/" + urlencode(key), httpClient)
        .auth(keycloak.tokenManager().getAccessTokenString())
        .json(rep)
        .asResponse();
    assertThat(resp.getStatus(), is(204));

    // cause an event to be sent
    sendEvent(keycloak, ev);

    // wait
    Thread.sleep(1000l);

    // check the handler for the event, after a delay
    assertThat(cnt.get(), is(1));
    assertNotNull(body.get());
    assertThat(body.get(), containsString("foo.BAR"));

    */

    removeEventListener(keycloak, "master", "ext-event-http");
    // wait and stop
    Thread.sleep(1000l);
    server.stop();
  }

  SimpleHttp.Response sendEvent(Keycloak keycloak, Map<String, String> ev) throws Exception {
    SimpleHttp.Response resp =
        SimpleHttp.doPost(eventsUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(ev)
            .asResponse();
    assertThat(resp.getStatus(), is(202));
    return resp;
  }
}
