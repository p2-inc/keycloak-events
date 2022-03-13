package io.phasetwo.keycloak.resources;

import static io.phasetwo.keycloak.Helpers.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.Assert.assertNotNull;

import com.github.xgp.http.server.Server;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.phasetwo.keycloak.KeycloakSuite;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.ClassRule;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.broker.provider.util.SimpleHttp;

@JBossLog
public class EventsResourceTest {

  @ClassRule public static KeycloakSuite server = KeycloakSuite.SERVER;

  CloseableHttpClient httpClient = HttpClients.createDefault();

  String baseUrl() {
    return server.getAuthUrl() + "/realms/master/events";
  }

  String webhookUrl() {
    return server.getAuthUrl() + "/realms/master/webhooks";
  }

  @Test
  public void testWebhookReceivesEvent() throws Exception {
    Keycloak keycloak = server.client();
    // update a realm with the ext-event-webhook listener
    addEventListener(keycloak, "master", "ext-event-webhook");

    AtomicReference<String> body = new AtomicReference<String>();
    // create a server on a free port with a handler to listen for the event
    int port = nextFreePort(8083, 10000);
    createWebhook(
        keycloak,
        httpClient,
        webhookUrl(),
        "http://127.0.0.1:" + port + "/webhook",
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

    Map<String, String> rep = ImmutableMap.of("type", "foo.BAR");
    // cause an event to be sent
    SimpleHttp.Response response =
        SimpleHttp.doPost(baseUrl(), httpClient)
            .auth(keycloak.tokenManager().getAccessTokenString())
            .json(rep)
            .asResponse();
    assertThat(response.getStatus(), is(202));

    Thread.sleep(1000l);

    // check the handler for the event, after a delay
    assertNotNull(body.get());
    assertThat(body.get(), containsString("foo.BAR"));

    server.stop();
  }
}
