package io.phasetwo.keycloak.events.format;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.fasterxml.jackson.core.type.TypeReference;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.OperationType;
import org.keycloak.util.JsonSerialization;

public class DatadogPayloadFormatterTest {

  final DatadogKeycloakPipeline pipeline = new DatadogKeycloakPipeline();
  final DatadogPayloadFormatter formatter = new DatadogPayloadFormatter();

  /** Sample lines from Datadog's Keycloak pipeline tests (keycloak_tests.yaml). */
  static final String DD_SAMPLE_CODE_TO_TOKEN =
      "type=\"CODE_TO_TOKEN\", realmId=\"f16e7899-3616-4c57-a8db-deab1da434c1\","
          + " realmName=\"master\", clientId=\"security-admin-console\","
          + " userId=\"15745079-6780-4b93-80d9-c0d3480885d3\","
          + " sessionId=\"25e60089-0911-4332-a7c3-71b17b038be5\", ipAddress=\"10.10.10.10\","
          + " token_id=\"71eea26a-b9c3-47bf-921b-c90c6cfe1c99\","
          + " grant_type=\"authorization_code\", refresh_token_type=\"Refresh\", scope=\"openid"
          + " email profile\", refresh_token_id=\"d1159936-cb35-4296-8ac2-524aa9af265b\","
          + " code_id=\"25e60089-0911-4332-a7c3-71b17b038be5\","
          + " client_auth_method=\"client-secret\"";

  static final String DD_SAMPLE_LOGIN_ERROR =
      "type=\"LOGIN_ERROR\", realmId=\"f16e7899-3616-4c57-a8db-deab1da434c1\","
          + " realmName=\"master\", clientId=\"security-admin-console\","
          + " userId=\"15745079-6780-4b93-80d9-c0d3480885d3\", ipAddress=\"10.10.10.10\","
          + " error=\"invalid_user_credentials\", auth_method=\"openid-connect\","
          + " auth_type=\"code\", username=\"admin\"";

  static final String DD_SAMPLE_ADMIN =
      "operationType=\"CREATE\", realmId=\"f16e7899-3616-4c57-a8db-deab1da434c1\","
          + " realmName=\"master\", clientId=\"21cba97c-4ce2-4272-8787-2f8016e43971\","
          + " userId=\"15745079-6780-4b93-80d9-c0d3480885d3\", ipAddress=\"10.10.10.10\","
          + " resourceType=\"AUTH_FLOW\","
          + " resourcePath=\"authentication/flows/3477e41a-8893-41ae-b6de-4dcfcf3f9006\"";

  static ExtendedAdminEvent userEvent(
      String type, String userId, String sessionId, String error, Map<String, String> details) {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setId("ev-1");
    e.setUid("uid-1");
    e.setType("access." + type);
    e.setTime(1700000000123L);
    e.setRealmId("f16e7899-3616-4c57-a8db-deab1da434c1");
    e.setRealmName("master");
    e.setError(error);
    ExtendedAuthDetails auth = new ExtendedAuthDetails(null);
    auth.setRealmId("master");
    auth.setClientId("security-admin-console");
    auth.setUserId(userId);
    auth.setSessionId(sessionId);
    auth.setIpAddress("10.10.10.10");
    e.setAuthDetails(auth);
    e.setDetails(details);
    return e;
  }

  @Test
  public void testUserEventMessageMatchesKeycloakLogLine() {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("token_id", "71eea26a-b9c3-47bf-921b-c90c6cfe1c99");
    details.put("grant_type", "authorization_code");
    details.put("refresh_token_type", "Refresh");
    details.put("scope", "openid email profile");
    details.put("refresh_token_id", "d1159936-cb35-4296-8ac2-524aa9af265b");
    details.put("code_id", "25e60089-0911-4332-a7c3-71b17b038be5");
    details.put("client_auth_method", "client-secret");
    ExtendedAdminEvent e =
        userEvent(
            "CODE_TO_TOKEN",
            "15745079-6780-4b93-80d9-c0d3480885d3",
            "25e60089-0911-4332-a7c3-71b17b038be5",
            null,
            details);

    String message = formatter.message(new EventView(e));
    assertThat(pipeline.matches(message), is(true));
    assertThat(pipeline.attributes(message), is(pipeline.attributes(DD_SAMPLE_CODE_TO_TOKEN)));
    // the fixed prefix is in Keycloak's order (details are a hash map, so their order may differ)
    assertThat(
        message.startsWith(
            DD_SAMPLE_CODE_TO_TOKEN.substring(0, DD_SAMPLE_CODE_TO_TOKEN.indexOf(", token_id"))),
        is(true));
  }

  @Test
  public void testErrorEventMessage() {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("auth_method", "openid-connect");
    details.put("auth_type", "code");
    details.put("username", "admin");
    ExtendedAdminEvent e =
        userEvent(
            "LOGIN_ERROR",
            "15745079-6780-4b93-80d9-c0d3480885d3",
            null,
            "invalid_user_credentials",
            details);
    String message = formatter.message(new EventView(e));
    assertThat(pipeline.matches(message), is(true));
    assertThat(pipeline.attributes(message), is(pipeline.attributes(DD_SAMPLE_LOGIN_ERROR)));
  }

  @Test
  public void testAdminEventMessage() {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setId("ev-2");
    e.setType("admin.AUTH_FLOW-CREATE");
    e.setOperationType(OperationType.CREATE);
    e.setRealmId("f16e7899-3616-4c57-a8db-deab1da434c1");
    e.setRealmName("master");
    e.setResourceTypeAsString("AUTH_FLOW");
    e.setResourcePath("authentication/flows/3477e41a-8893-41ae-b6de-4dcfcf3f9006");
    ExtendedAuthDetails auth = new ExtendedAuthDetails(null);
    auth.setClientId("21cba97c-4ce2-4272-8787-2f8016e43971");
    auth.setUserId("15745079-6780-4b93-80d9-c0d3480885d3");
    auth.setIpAddress("10.10.10.10");
    e.setAuthDetails(auth);

    String message = formatter.message(new EventView(e));
    assertThat(message, is(DD_SAMPLE_ADMIN));
    assertThat(pipeline.matches(message), is(true));
  }

  @Test
  public void testNullUserAndQuotesAreSanitizedLikeKeycloak() {
    ExtendedAdminEvent e =
        userEvent(
            "LOGIN_ERROR",
            null,
            null,
            "user_not_found",
            Map.of("username", "evil\" injected=\"1\nnext"));
    String message = formatter.message(new EventView(e));
    assertThat(message.contains("userId=\"null\""), is(true));
    assertThat(message.contains("username=\"evil\\\" injected=\\\"1 next\""), is(true));
    assertThat(pipeline.matches(message), is(true));
  }

  @Test
  public void testEntry() throws Exception {
    ExtendedAdminEvent e =
        userEvent(
            "LOGIN_ERROR",
            "u-1",
            "s-1",
            "invalid_user_credentials",
            Map.of("username", "jdoe", "email", "jdoe@example.com"));
    FormattedPayload p =
        new DatadogPayloadFormatter()
            .format(List.of(e), Map.of("tags", "env:test", "hostname", "kc-1"));
    List<Map<String, Object>> entries =
        JsonSerialization.readValue(p.body(), new TypeReference<List<Map<String, Object>>>() {});
    assertThat(entries.size(), is(1));
    Map<String, Object> entry = entries.get(0);
    assertThat(entry.get("ddsource"), is("keycloak"));
    assertThat(entry.get("service"), is("keycloak"));
    assertThat(entry.get("hostname"), is("kc-1"));
    assertThat(entry.get("ddtags"), is("env:test"));
    assertThat(entry.get("status"), is("warn"));
    assertThat(entry.get("loggerName"), is("org.keycloak.events"));
    assertThat(((Number) entry.get("timestamp")).longValue(), is(1700000000123L));
    assertThat(
        entry.get("evt"),
        is(Map.of("name", "LOGIN_ERROR", "outcome", "failure", "category", "authentication")));
    assertThat(
        entry.get("usr"), is(Map.of("id", "u-1", "name", "jdoe", "email", "jdoe@example.com")));
    assertThat(entry.get("network"), is(Map.of("client", Map.of("ip", "10.10.10.10"))));
    assertThat(p.contentType(), is("application/json"));
  }

  @Test
  public void testDefaultsAndNoTags() throws Exception {
    ExtendedAdminEvent e = userEvent("LOGIN", "u-1", "s-1", null, Map.of());
    Map<String, Object> entry = formatter.entry(new EventView(e), Map.of());
    assertThat(entry, not(hasKey("ddtags")));
    assertThat(entry.get("status"), is("info"));
    assertThat(((Map<?, ?>) entry.get("evt")).get("outcome"), is("success"));
  }

  @Test
  public void testRetryPolicy() {
    DatadogPayloadFormatter f = new DatadogPayloadFormatter();
    for (int s : new int[] {400, 401, 403, 413}) assertThat(f.isRetryable(s), is(false));
    for (int s : new int[] {408, 429, 500, 503}) assertThat(f.isRetryable(s), is(true));
  }

  @Test
  public void testAdminTargetUserDetailsDoNotCollideWithActor() {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setType("admin.USER-CREATE");
    e.setOperationType(OperationType.CREATE);
    e.setRealmName("master");
    e.setResourceTypeAsString("USER");
    e.setResourcePath("users/target-id");
    ExtendedAuthDetails auth = new ExtendedAuthDetails(null);
    auth.setUserId("admin-id");
    e.setAuthDetails(auth);
    e.setDetails(Map.of("userId", "target-id", "username", "target"));

    String message = formatter.message(new EventView(e));
    Map<String, String> kv = pipeline.attributes(message);
    assertThat(kv.get("userId"), is("admin-id"));
    assertThat(kv.get("resourceUserId"), is("target-id"));
    assertThat(kv.get("resourceUsername"), is("target"));
    assertThat(kv.containsKey("username"), is(false));
    assertThat(message.indexOf("userId=") == message.lastIndexOf("userId="), is(true));
  }

  @Test
  public void testUserEventDetailCollidingWithFixedKeyIsPrefixed() {
    ExtendedAdminEvent e = userEvent("LOGIN", "u-1", "s-1", null, Map.of("clientId", "other"));
    Map<String, String> kv = pipeline.attributes(formatter.message(new EventView(e)));
    assertThat(kv.get("clientId"), is("security-admin-console"));
    assertThat(kv.get("detail_clientId"), is("other"));
  }
}
