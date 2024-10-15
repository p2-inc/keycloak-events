package io.phasetwo.keycloak.events;

import static io.phasetwo.keycloak.Helpers.createWebhook;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import com.github.xgp.http.server.Server;
import com.google.common.collect.ImmutableSet;

import io.phasetwo.keycloak.resources.AbstractResourceTest;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class WebhookSenderEventListenerProviderTest extends AbstractResourceTest {
  CloseableHttpClient httpClient = HttpClients.createDefault();

  String webhookUrl(String realm) {
    return getAuthUrl() + "/realms/" + realm + "/webhooks";
  }

  String usersUrl(String realm) {
    return getAuthUrl() + "/admin/realms/" + realm + "/users";
  }

  @Test
  public void testAdminEventContainsCorrectRealm() throws Exception {
    // Create a realm for tests
    final String TEST_REALM = "testRealm";
    RealmRepresentation realmRepresentation = new RealmRepresentation();
    realmRepresentation.setId(TEST_REALM);
    realmRepresentation.setDisplayName(TEST_REALM);
    realmRepresentation.setAdminEventsEnabled(true);
    realmRepresentation.setAdminEventsDetailsEnabled(true);
    realmRepresentation.setRealm(TEST_REALM);
    realmRepresentation.setEventsListeners(Arrays.asList("ext-event-webhook"));
    keycloak.realms().create(realmRepresentation);
    RealmResource realm = keycloak.realm(TEST_REALM);

    AtomicReference<String> body = new AtomicReference<String>();
    // create a server on a free port with a handler to listen for the event
    int port = WEBHOOK_SERVER_PORT;
    createWebhook(
        keycloak,
        httpClient,
        webhookUrl(TEST_REALM),
        "http://host.testcontainers.internal:" + port + "/webhook",
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
    UserRepresentation userRepresentation = new UserRepresentation();
    userRepresentation.setUsername("username");
    Response userResponse = realm.users().create(userRepresentation);
    assertThat(userResponse.getStatus(), is(201));

    Thread.sleep(1000l);

    // check the handler for the event, after a delay
    String receivedPayload = body.get();
    assertThat(receivedPayload, containsString(TEST_REALM));
    assertThat(receivedPayload, not(containsString("master")));

    server.stop();

  }
}
