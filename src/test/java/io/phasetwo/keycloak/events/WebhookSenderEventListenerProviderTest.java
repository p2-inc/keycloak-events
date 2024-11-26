package io.phasetwo.keycloak.events;

import static io.phasetwo.keycloak.Helpers.createWebhook;
import static io.phasetwo.keycloak.Helpers.removeWebhook;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;

import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.xgp.http.server.Server;
import com.google.common.collect.ImmutableSet;

import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.resources.AbstractResourceTest;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class WebhookSenderEventListenerProviderTest extends AbstractResourceTest {
  final static String TEST_REALM = "testRealm";
  CloseableHttpClient httpClient = HttpClients.createDefault();

  String webhookUrl(String realm) {
    return getAuthUrl() + "/realms/" + realm + "/webhooks";
  }

  String usersUrl(String realm) {
    return getAuthUrl() + "/admin/realms/" + realm + "/users";
  }

  @Test
  public void testAdminEventContainsCorrectRealmMasterMaster() throws Exception {
    // Configure
    RealmResource realm = keycloak.realm(REALM);
    RealmRepresentation realmRepresentation = realm.toRepresentation();
    realmRepresentation.setAdminEventsEnabled(true);
    realmRepresentation.setAdminEventsDetailsEnabled(true);
    realmRepresentation.setEventsListeners(Arrays.asList("ext-event-webhook"));
    realm.update(realmRepresentation);

    AtomicReference<String> body = new AtomicReference<String>();
    // create a server on a free port with a handler to listen for the event
    int port = WEBHOOK_SERVER_PORT;
    String webhookId = createWebhook(
        keycloak,
        httpClient,
        webhookUrl(REALM),
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

    try {
	    // cause an event to be sent
	    UserRepresentation userRepresentation = new UserRepresentation();
	    userRepresentation.setUsername("username");
	    Response userResponse = realm.users().create(userRepresentation);
	    assertThat(userResponse.getStatus(), is(201));

	    Thread.sleep(1000l);

	    // check the handler for the event, after a delay
	    String receivedPayload = body.get();
	    ExtendedAdminEvent event = parseEvent(receivedPayload);
	    assertThat(event.getRealmId(), equalTo(REALM));
	    assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
	    assertThat(event.getType(), equalTo("admin.USER-CREATE"));

	    // Now delete the user
	    List<UserRepresentation> users = realm.users().search("username");
	    assertThat(users.size(), is(1));
	    String userId = users.getFirst().getId();
	    userResponse = realm.users().delete(userId);
	    assertThat(userResponse.getStatus(), is(204));
	    
	    Thread.sleep(1000l);

     	// check the handler for the event, after a delay
	    receivedPayload = body.get();
	    event = parseEvent(receivedPayload);
	    assertThat(event.getRealmId(), equalTo(REALM));
	    assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
	    assertThat(event.getType(), equalTo("admin.USER-DELETE"));
    }
    finally {
        server.stop();
        removeWebhook(keycloak,
                httpClient,
                webhookUrl(REALM),
                webhookId);
    }
  }

  @Test
  public void testAdminEventContainsCorrectRealmMasterTest() throws Exception {
    // Create a realm for tests
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

    try {
	    // cause an event to be sent
	    UserRepresentation userRepresentation = new UserRepresentation();
	    userRepresentation.setUsername("username");
	    Response userResponse = realm.users().create(userRepresentation);
	    assertThat(userResponse.getStatus(), is(201));

	    Thread.sleep(1000l);

	    // check the handler for the event, after a delay
	    String receivedPayload = body.get();
	    ExtendedAdminEvent event = parseEvent(receivedPayload);
	    assertThat(event.getRealmId(), equalTo(TEST_REALM));
	    assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
	    assertThat(event.getType(), equalTo("admin.USER-CREATE"));

	    // Now delete the user
	    List<UserRepresentation> users = realm.users().search("username");
	    assertThat(users.size(), is(1));
	    String userId = users.getFirst().getId();
	    userResponse = realm.users().delete(userId);
	    assertThat(userResponse.getStatus(), is(204));
	    
	    Thread.sleep(1000l);

     	// check the handler for the event, after a delay
	    receivedPayload = body.get();
	    event = parseEvent(receivedPayload);
	    assertThat(event.getRealmId(), equalTo(TEST_REALM));
	    assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
	    assertThat(event.getType(), equalTo("admin.USER-DELETE"));

	    // cleanup
	    realm.remove();
    }
    finally {
        server.stop();
    }
  }

  @Test
  public void testAdminEventContainsCorrectRealmTestTest() throws Exception {
    // Create a realm for tests
    RealmRepresentation realmRepresentation = new RealmRepresentation();
    realmRepresentation.setId(TEST_REALM);
    realmRepresentation.setDisplayName(TEST_REALM);
    realmRepresentation.setAdminEventsEnabled(true);
    realmRepresentation.setAdminEventsDetailsEnabled(true);
    realmRepresentation.setRealm(TEST_REALM);
    realmRepresentation.setEventsListeners(Arrays.asList("ext-event-webhook"));
    realmRepresentation.setEnabled(true);
    keycloak.realms().create(realmRepresentation);
    RealmResource realm = keycloak.realm(TEST_REALM);

    // Creating admin client
    final String realmAdminClientId = "realmAdmin";
    final String realmAdminClientSecret = "realmPassword";
    ClientRepresentation client = new ClientRepresentation();
    client.setSecret(realmAdminClientSecret);
    client.setClientId(realmAdminClientId);
    client.setEnabled(true);
    client.setServiceAccountsEnabled(true);
    client.setPublicClient(false);
    client.setProtocol("openid-connect");
    Response clientCreationResponse = realm.clients().create(client);
    assertThat(clientCreationResponse.getStatus(), is(201));

    // Adding rights
    String realmManagementId = realm.clients().findByClientId("realm-management").get(0).getId();
    String clientId = realm.clients().findByClientId(realmAdminClientId).get(0).getId();
    String serviceUserId = realm.clients().get(clientId).getServiceAccountUser().getId();
    List<RoleRepresentation> availableRoles = realm
            .users()
            .get(serviceUserId)
            .roles()
            .clientLevel(realmManagementId)
            .listAvailable();
    List<RoleRepresentation> rolesToAssign = availableRoles
            .stream()
            .filter(r ->
                "realm-admin".equalsIgnoreCase(r.getName()))
            .collect(Collectors.toList());
    assertThat(rolesToAssign.size(), is(1));
    realm.users().get(serviceUserId).roles().clientLevel(realmManagementId).add(rolesToAssign);

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

    try {
        // log in to the test realm
        Keycloak keycloakTestRealm = KeycloakBuilder.builder()
                .serverUrl(getAuthUrl())
                .clientId(realmAdminClientId)
                .clientSecret(realmAdminClientSecret)
                .realm(TEST_REALM)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
        realm = keycloakTestRealm.realm(TEST_REALM);

	    // cause an event to be sent
	    UserRepresentation userRepresentation = new UserRepresentation();
	    userRepresentation.setUsername("username");
	    Response userResponse = realm.users().create(userRepresentation);
	    assertThat(userResponse.getStatus(), is(201));

	    Thread.sleep(1000l);

	    // check the handler for the event, after a delay
	    String receivedPayload = body.get();
	    ExtendedAdminEvent event = parseEvent(receivedPayload);
	    assertThat(event.getRealmId(), equalTo(TEST_REALM));
	    assertThat(event.getAuthDetails().getRealmId(), equalTo(TEST_REALM));
	    assertThat(event.getType(), equalTo("admin.USER-CREATE"));

	    // Now delete the user
	    List<UserRepresentation> users = realm.users().search("username");
	    assertThat(users.size(), is(1));
	    String userId = users.getFirst().getId();
	    userResponse = realm.users().delete(userId);
	    assertThat(userResponse.getStatus(), is(204));
	    
	    Thread.sleep(1000l);

     	// check the handler for the event, after a delay
	    receivedPayload = body.get();
	    event = parseEvent(receivedPayload);
	    assertThat(event.getRealmId(), equalTo(TEST_REALM));
	    assertThat(event.getAuthDetails().getRealmId(), equalTo(TEST_REALM));
	    assertThat(event.getType(), equalTo("admin.USER-DELETE"));

	    // cleanup
	    realm = keycloak.realm(TEST_REALM);
	    realm.remove();
    }
    finally {
        server.stop();
    }
  }

  private static ExtendedAdminEvent parseEvent(String input) throws Exception
  {
	  ObjectMapper mapper = new ObjectMapper();
	  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	  return mapper.readValue(input, ExtendedAdminEvent.class);
  }
}
