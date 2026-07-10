package io.phasetwo.keycloak.events;

import static io.phasetwo.keycloak.Helpers.createBearerWebhook;
import static io.phasetwo.keycloak.Helpers.createUnauthenticatedWebhook;
import static io.phasetwo.keycloak.Helpers.createWebhook;
import static io.phasetwo.keycloak.Helpers.removeWebhook;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.xgp.http.server.Server;
import com.google.common.collect.ImmutableSet;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.resources.AbstractResourceTest;
import jakarta.ws.rs.core.Response;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.provider.util.LegacySimpleHttp;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKParser;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.util.JsonSerialization;

@JBossLog
public class WebhookSenderEventListenerProviderTest extends AbstractResourceTest {
  static final String TEST_REALM = "testRealm";
  static final Duration WEBHOOK_AWAIT = Duration.ofSeconds(15);
  static final Duration WEBHOOK_POLL = Duration.ofMillis(100);

  CloseableHttpClient httpClient = HttpClients.createDefault();

  String webhookUrl(String realm) {
    return getAuthUrl() + "/realms/" + realm + "/webhooks";
  }

  String usersUrl(String realm) {
    return getAuthUrl() + "/admin/realms/" + realm + "/users";
  }

  @Test
  public void testWebhookEventsAndSendsStorage() throws Exception {
    // Configure
    RealmResource realm = keycloak.realm(REALM);
    RealmRepresentation realmRepresentation = realm.toRepresentation();
    realmRepresentation.setEventsEnabled(true);
    realmRepresentation.setAdminEventsEnabled(true);
    realmRepresentation.setAdminEventsDetailsEnabled(true);
    realmRepresentation.setEventsListeners(Arrays.asList("ext-event-webhook"));
    realm.update(realmRepresentation);

    AtomicReference<String> body = new AtomicReference<String>();
    int port = WEBHOOK_SERVER_PORT;
    String webhookId =
        createWebhook(
            keycloak,
            httpClient,
            webhookUrl(REALM),
            "http://host.testcontainers.internal:" + port + "/webhook",
            "qlfwemke",
            ImmutableSet.of("admin.*"));

    Server server = newWebhookServer(port, body);
    server.start();
    Thread.sleep(1000l);

    String userId = null;
    try {
      // cause an event to be sent
      UserRepresentation userRepresentation = new UserRepresentation();
      userRepresentation.setUsername("username");
      Response userResponse = realm.users().create(userRepresentation);
      assertThat(userResponse.getStatus(), is(201));

      // check the handler for the event, after polling
      ExtendedAdminEvent event = parseEvent(awaitBody(body, "admin.USER-CREATE on master"));
      assertThat(event.getRealmName(), equalTo(REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
      assertThat(event.getType(), equalTo("admin.USER-CREATE"));

      // Now delete the user
      List<UserRepresentation> users = realm.users().search("username");
      assertThat(users.size(), is(1));
      userId = users.get(0).getId();
      userResponse = realm.users().delete(userId);
      assertThat(userResponse.getStatus(), is(204));
      userId = null; // cleanup not needed if delete succeeded

      event = parseEvent(awaitBody(body, "admin.USER-DELETE on master"));
      assertThat(event.getRealmName(), equalTo(REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
      assertThat(event.getType(), equalTo("admin.USER-DELETE"));
    } finally {
      server.stop();
      bestEffortDeleteUser(realm, userId);
      bestEffortRemoveWebhook(keycloak, httpClient, webhookUrl(REALM), webhookId);
    }
  }

  @Test
  public void testAdminEventContainsCorrectRealmMasterMaster() throws Exception {
    // Configure
    RealmResource realm = keycloak.realm(REALM);
    RealmRepresentation realmRepresentation = realm.toRepresentation();
    realmRepresentation.setEventsEnabled(true);
    realmRepresentation.setAdminEventsEnabled(true);
    realmRepresentation.setAdminEventsDetailsEnabled(true);
    realmRepresentation.setEventsListeners(Arrays.asList("ext-event-webhook"));
    realm.update(realmRepresentation);

    AtomicReference<String> body = new AtomicReference<String>();
    int port = WEBHOOK_SERVER_PORT;
    String webhookId =
        createWebhook(
            keycloak,
            httpClient,
            webhookUrl(REALM),
            "http://host.testcontainers.internal:" + port + "/webhook",
            "qlfwemke",
            ImmutableSet.of("admin.*"));

    Server server = newWebhookServer(port, body);
    server.start();
    Thread.sleep(1000l);

    String userId = null;
    try {
      // cause an event to be sent
      UserRepresentation userRepresentation = new UserRepresentation();
      userRepresentation.setUsername("username");
      Response userResponse = realm.users().create(userRepresentation);
      assertThat(userResponse.getStatus(), is(201));

      // check the handler for the event, after polling
      ExtendedAdminEvent event = parseEvent(awaitBody(body, "admin.USER-CREATE on master"));
      assertThat(event.getRealmName(), equalTo(REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
      assertThat(event.getType(), equalTo("admin.USER-CREATE"));

      // Now delete the user
      List<UserRepresentation> users = realm.users().search("username");
      assertThat(users.size(), is(1));
      userId = users.get(0).getId();
      userResponse = realm.users().delete(userId);
      assertThat(userResponse.getStatus(), is(204));
      userId = null;

      event = parseEvent(awaitBody(body, "admin.USER-DELETE on master"));
      assertThat(event.getRealmName(), equalTo(REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
      assertThat(event.getType(), equalTo("admin.USER-DELETE"));
    } finally {
      server.stop();
      bestEffortDeleteUser(realm, userId);
      bestEffortRemoveWebhook(keycloak, httpClient, webhookUrl(REALM), webhookId);
    }
  }

  @Test
  public void testWebhookBearerJwtAuth() throws Exception {
    // Configure
    RealmResource realm = keycloak.realm(REALM);
    RealmRepresentation realmRepresentation = realm.toRepresentation();
    realmRepresentation.setEventsEnabled(true);
    realmRepresentation.setAdminEventsEnabled(true);
    realmRepresentation.setAdminEventsDetailsEnabled(true);
    realmRepresentation.setEventsListeners(Arrays.asList("ext-event-webhook"));
    realm.update(realmRepresentation);

    AtomicReference<String> body = new AtomicReference<String>();
    AtomicReference<Map<String, String>> headers = new AtomicReference<Map<String, String>>();
    int port = WEBHOOK_SERVER_PORT;
    String audience = "https://receiver.example.com";
    String webhookId =
        createBearerWebhook(
            keycloak,
            httpClient,
            webhookUrl(REALM),
            "http://host.testcontainers.internal:" + port + "/webhook",
            audience,
            ImmutableSet.of("admin.*"));

    Server server = newWebhookServer(port, body, headers);
    server.start();
    Thread.sleep(1000l);

    String userId = null;
    try {
      // cause an event to be sent
      UserRepresentation userRepresentation = new UserRepresentation();
      userRepresentation.setUsername("jwtuser");
      Response userResponse = realm.users().create(userRepresentation);
      assertThat(userResponse.getStatus(), is(201));

      String payload = awaitBody(body, "admin.USER-CREATE on master (bearer)");
      ExtendedAdminEvent event = parseEvent(payload);
      assertThat(event.getType(), equalTo("admin.USER-CREATE"));

      // the payload is authenticated via an Authorization: Bearer JWT, not a shared secret
      String auth = headers.get().get("Authorization");
      assertThat(auth, notNullValue());
      assertThat(auth, startsWith("Bearer "));
      String jwt = auth.substring("Bearer ".length());

      // verify the JWT signature against the realm JWKS and validate its claims
      JsonWebToken token = verifyJwtAgainstRealmJwks(jwt, REALM);
      assertThat(token.getIssuer(), endsWith("/realms/" + REALM));
      assertThat(Arrays.asList(token.getAudience()), hasItem(audience));
      assertThat(token.getExp() > System.currentTimeMillis() / 1000L, is(true));
      // the request_body_sha256 claim binds the token to this exact payload
      String bodyHash = (String) token.getOtherClaims().get("request_body_sha256");
      assertThat(bodyHash, equalTo(WebhookJwtSigner.sha256Hex(payload)));

      List<UserRepresentation> users = realm.users().search("jwtuser");
      assertThat(users.size(), is(1));
      userId = users.get(0).getId();
    } finally {
      server.stop();
      bestEffortDeleteUser(realm, userId);
      bestEffortRemoveWebhook(keycloak, httpClient, webhookUrl(REALM), webhookId);
    }
  }

  @Test
  public void testWebhookNoneAuth() throws Exception {
    // Configure
    RealmResource realm = keycloak.realm(REALM);
    RealmRepresentation realmRepresentation = realm.toRepresentation();
    realmRepresentation.setEventsEnabled(true);
    realmRepresentation.setAdminEventsEnabled(true);
    realmRepresentation.setAdminEventsDetailsEnabled(true);
    realmRepresentation.setEventsListeners(Arrays.asList("ext-event-webhook"));
    realm.update(realmRepresentation);

    AtomicReference<String> body = new AtomicReference<String>();
    AtomicReference<Map<String, String>> headers = new AtomicReference<Map<String, String>>();
    int port = WEBHOOK_SERVER_PORT;
    String webhookId =
        createUnauthenticatedWebhook(
            keycloak,
            httpClient,
            webhookUrl(REALM),
            "http://host.testcontainers.internal:" + port + "/webhook",
            ImmutableSet.of("admin.*"));

    Server server = newWebhookServer(port, body, headers);
    server.start();
    Thread.sleep(1000l);

    String userId = null;
    try {
      // cause an event to be sent
      UserRepresentation userRepresentation = new UserRepresentation();
      userRepresentation.setUsername("noneuser");
      Response userResponse = realm.users().create(userRepresentation);
      assertThat(userResponse.getStatus(), is(201));

      String payload = awaitBody(body, "admin.USER-CREATE on master (none)");
      ExtendedAdminEvent event = parseEvent(payload);
      assertThat(event.getType(), equalTo("admin.USER-CREATE"));

      // an unauthenticated webhook sends neither a signature nor a bearer token
      assertThat(headers.get().get("Authorization"), nullValue());
      assertThat(headers.get().get("X-Keycloak-Signature"), nullValue());

      List<UserRepresentation> users = realm.users().search("noneuser");
      assertThat(users.size(), is(1));
      userId = users.get(0).getId();
    } finally {
      server.stop();
      bestEffortDeleteUser(realm, userId);
      bestEffortRemoveWebhook(keycloak, httpClient, webhookUrl(REALM), webhookId);
    }
  }

  /** Verify a compact JWS against the realm's published JWKS and return the parsed token. */
  private JsonWebToken verifyJwtAgainstRealmJwks(String jwt, String realmName) throws Exception {
    String certsUrl = getAuthUrl() + "/realms/" + realmName + "/protocol/openid-connect/certs";
    String certs = LegacySimpleHttp.doGet(certsUrl, httpClient).asString();
    JSONWebKeySet jwks = JsonSerialization.readValue(certs, JSONWebKeySet.class);
    String kid = new JWSInput(jwt).getHeader().getKeyId();
    PublicKey publicKey = null;
    for (JWK jwk : jwks.getKeys()) {
      if (kid.equals(jwk.getKeyId())) {
        publicKey = JWKParser.create(jwk).toPublicKey();
        break;
      }
    }
    assertThat("no JWKS key matching kid " + kid, publicKey, notNullValue());
    TokenVerifier<JsonWebToken> verifier =
        TokenVerifier.create(jwt, JsonWebToken.class).publicKey(publicKey);
    verifier.verify(); // throws on bad signature
    return verifier.getToken();
  }

  @Test
  public void testAdminEventContainsCorrectRealmMasterTest() throws Exception {
    // Create a realm for tests
    RealmRepresentation realmRepresentation = new RealmRepresentation();
    realmRepresentation.setId(TEST_REALM);
    realmRepresentation.setDisplayName(TEST_REALM);
    realmRepresentation.setEventsEnabled(true);
    realmRepresentation.setAdminEventsEnabled(true);
    realmRepresentation.setAdminEventsDetailsEnabled(true);
    realmRepresentation.setRealm(TEST_REALM);
    realmRepresentation.setEventsListeners(Arrays.asList("ext-event-webhook"));
    keycloak.realms().create(realmRepresentation);
    RealmResource realm = keycloak.realm(TEST_REALM);

    AtomicReference<String> body = new AtomicReference<String>();
    int port = WEBHOOK_SERVER_PORT;
    String webhookId =
        createWebhook(
            keycloak,
            httpClient,
            webhookUrl(TEST_REALM),
            "http://host.testcontainers.internal:" + port + "/webhook",
            "qlfwemke",
            ImmutableSet.of("admin.*"));

    Server server = newWebhookServer(port, body);
    server.start();
    Thread.sleep(1000l);

    try {
      // cause an event to be sent
      UserRepresentation userRepresentation = new UserRepresentation();
      userRepresentation.setUsername("username");
      Response userResponse = realm.users().create(userRepresentation);
      assertThat(userResponse.getStatus(), is(201));

      ExtendedAdminEvent event = parseEvent(awaitBody(body, "admin.USER-CREATE on testRealm"));
      assertThat(event.getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
      assertThat(event.getType(), equalTo("admin.USER-CREATE"));

      // Now delete the user
      List<UserRepresentation> users = realm.users().search("username");
      assertThat(users.size(), is(1));
      String userId = users.get(0).getId();
      userResponse = realm.users().delete(userId);
      assertThat(userResponse.getStatus(), is(204));

      event = parseEvent(awaitBody(body, "admin.USER-DELETE on testRealm"));
      assertThat(event.getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
      assertThat(event.getType(), equalTo("admin.USER-DELETE"));
    } finally {
      server.stop();
      bestEffortRemoveWebhook(keycloak, httpClient, webhookUrl(TEST_REALM), webhookId);
      bestEffortRemoveRealm(keycloak, TEST_REALM);
    }
  }

  @Test
  public void testAdminEventContainsCorrectRealmTestTest() throws Exception {
    // Create a realm for tests
    RealmRepresentation realmRepresentation = new RealmRepresentation();
    realmRepresentation.setId(TEST_REALM);
    realmRepresentation.setDisplayName(TEST_REALM);
    realmRepresentation.setEventsEnabled(true);
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
    List<RoleRepresentation> availableRoles =
        realm.users().get(serviceUserId).roles().clientLevel(realmManagementId).listAvailable();
    List<RoleRepresentation> rolesToAssign =
        availableRoles.stream()
            .filter(r -> "realm-admin".equalsIgnoreCase(r.getName()))
            .collect(Collectors.toList());
    assertThat(rolesToAssign.size(), is(1));
    realm.users().get(serviceUserId).roles().clientLevel(realmManagementId).add(rolesToAssign);

    AtomicReference<String> body = new AtomicReference<String>();
    int port = WEBHOOK_SERVER_PORT;
    String webhookId =
        createWebhook(
            keycloak,
            httpClient,
            webhookUrl(TEST_REALM),
            "http://host.testcontainers.internal:" + port + "/webhook",
            "qlfwemke",
            ImmutableSet.of("admin.*"));

    Server server = newWebhookServer(port, body);
    server.start();
    Thread.sleep(1000l);

    try {
      // log in to the test realm
      Keycloak keycloakTestRealm =
          KeycloakBuilder.builder()
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

      ExtendedAdminEvent event =
          parseEvent(awaitBody(body, "admin.USER-CREATE on testRealm (by test admin)"));
      assertThat(event.getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getType(), equalTo("admin.USER-CREATE"));

      // Now delete the user
      List<UserRepresentation> users = realm.users().search("username");
      assertThat(users.size(), is(1));
      String userId = users.get(0).getId();
      userResponse = realm.users().delete(userId);
      assertThat(userResponse.getStatus(), is(204));

      event = parseEvent(awaitBody(body, "admin.USER-DELETE on testRealm (by test admin)"));
      assertThat(event.getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getType(), equalTo("admin.USER-DELETE"));
    } finally {
      server.stop();
      bestEffortRemoveWebhook(keycloak, httpClient, webhookUrl(TEST_REALM), webhookId);
      bestEffortRemoveRealm(keycloak, TEST_REALM);
    }
  }

  @Test
  public void testAdminEventContainsCorrectRealmCreatedByMaterRemovedByTest() throws Exception {
    // Create a realm for tests
    RealmRepresentation realmRepresentation = new RealmRepresentation();
    realmRepresentation.setId(TEST_REALM);
    realmRepresentation.setDisplayName(TEST_REALM);
    realmRepresentation.setEventsEnabled(true);
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
    List<RoleRepresentation> availableRoles =
        realm.users().get(serviceUserId).roles().clientLevel(realmManagementId).listAvailable();
    List<RoleRepresentation> rolesToAssign =
        availableRoles.stream()
            .filter(r -> "realm-admin".equalsIgnoreCase(r.getName()))
            .collect(Collectors.toList());
    assertThat(rolesToAssign.size(), is(1));
    realm.users().get(serviceUserId).roles().clientLevel(realmManagementId).add(rolesToAssign);

    AtomicReference<String> body = new AtomicReference<String>();
    int port = WEBHOOK_SERVER_PORT;
    String webhookId =
        createWebhook(
            keycloak,
            httpClient,
            webhookUrl(TEST_REALM),
            "http://host.testcontainers.internal:" + port + "/webhook",
            "qlfwemke",
            ImmutableSet.of("admin.*"));

    Server server = newWebhookServer(port, body);
    server.start();
    Thread.sleep(1000l);

    try {
      // cause an event to be sent
      UserRepresentation userRepresentation = new UserRepresentation();
      userRepresentation.setUsername("username");
      Response userResponse = realm.users().create(userRepresentation);
      assertThat(userResponse.getStatus(), is(201));

      ExtendedAdminEvent event =
          parseEvent(awaitBody(body, "admin.USER-CREATE on testRealm (created by master)"));
      assertThat(event.getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(REALM));
      assertThat(event.getType(), equalTo("admin.USER-CREATE"));

      // log in to the test realm
      Keycloak keycloakTestRealm =
          KeycloakBuilder.builder()
              .serverUrl(getAuthUrl())
              .clientId(realmAdminClientId)
              .clientSecret(realmAdminClientSecret)
              .realm(TEST_REALM)
              .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
              .build();
      realm = keycloakTestRealm.realm(TEST_REALM);

      // Now delete the user
      List<UserRepresentation> users = realm.users().search("username");
      assertThat(users.size(), is(1));
      String userId = users.get(0).getId();
      userResponse = realm.users().delete(userId);
      assertThat(userResponse.getStatus(), is(204));

      event = parseEvent(awaitBody(body, "admin.USER-DELETE on testRealm (by test admin)"));
      assertThat(event.getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getAuthDetails().getRealmId(), equalTo(TEST_REALM));
      assertThat(event.getType(), equalTo("admin.USER-DELETE"));
    } finally {
      server.stop();
      bestEffortRemoveWebhook(keycloak, httpClient, webhookUrl(TEST_REALM), webhookId);
      bestEffortRemoveRealm(keycloak, TEST_REALM);
    }
  }

  private static Server newWebhookServer(int port, AtomicReference<String> body) {
    return newWebhookServer(port, body, null);
  }

  /**
   * Webhook server that, in addition to capturing the body, records the authentication-related
   * request headers ({@code Authorization} and {@code X-Keycloak-Signature}) into {@code headers}
   * when provided. Missing headers are stored as {@code null}.
   */
  private static Server newWebhookServer(
      int port, AtomicReference<String> body, AtomicReference<Map<String, String>> headers) {
    Server server = new Server(port);
    server
        .router()
        .POST(
            "/webhook",
            (request, response) -> {
              String r = request.body();
              log.infof("%s", r);
              if (headers != null) {
                Map<String, String> captured = new HashMap<>();
                captured.put("Authorization", request.header("Authorization"));
                captured.put("X-Keycloak-Signature", request.header("X-Keycloak-Signature"));
                headers.set(captured);
              }
              body.set(r);
              response.body("OK");
              response.status(202);
            });
    return server;
  }

  /**
   * Polls {@code body} until a payload arrives, consuming it via {@link
   * AtomicReference#getAndSet(Object)} so the next call waits for the next webhook delivery instead
   * of re-reading a stale value.
   */
  private static String awaitBody(AtomicReference<String> body, String description) {
    long deadline = System.nanoTime() + WEBHOOK_AWAIT.toNanos();
    while (System.nanoTime() < deadline) {
      String payload = body.getAndSet(null);
      if (payload != null) return payload;
      try {
        Thread.sleep(WEBHOOK_POLL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while awaiting " + description);
      }
    }
    throw new AssertionError("Timed out after " + WEBHOOK_AWAIT + " awaiting " + description);
  }

  private static void bestEffortDeleteUser(RealmResource realm, String userId) {
    if (userId == null) return;
    try {
      realm.users().delete(userId).close();
    } catch (Exception e) {
      log.warnf("cleanup: failed to delete user %s: %s", userId, e.getMessage());
    }
  }

  private static void bestEffortRemoveWebhook(
      Keycloak keycloak, CloseableHttpClient httpClient, String url, String webhookId) {
    if (webhookId == null) return;
    try {
      removeWebhook(keycloak, httpClient, url, webhookId);
    } catch (Exception e) {
      log.warnf("cleanup: failed to remove webhook %s: %s", webhookId, e.getMessage());
    }
  }

  private static void bestEffortRemoveRealm(Keycloak keycloak, String realmName) {
    try {
      keycloak.realm(realmName).remove();
    } catch (Exception e) {
      log.warnf("cleanup: failed to remove realm %s: %s", realmName, e.getMessage());
    }
  }

  private static ExtendedAdminEvent parseEvent(String input) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper.readValue(input, ExtendedAdminEvent.class);
  }
}
