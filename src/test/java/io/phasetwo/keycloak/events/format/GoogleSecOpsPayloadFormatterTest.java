package io.phasetwo.keycloak.events.format;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.OperationType;
import org.keycloak.util.JsonSerialization;

public class GoogleSecOpsPayloadFormatterTest {

  /** The top-level fields of a Datastream change record the KEYCLOAK parser reads. */
  public static final Set<String> ENVELOPE_FIELDS =
      Set.of(
          "uuid",
          "read_timestamp",
          "source_timestamp",
          "object",
          "read_method",
          "source_metadata",
          "payload");

  static Map<String, Object> parse(FormattedPayload p) throws Exception {
    return JsonSerialization.readValue(p.body(), new TypeReference<Map<String, Object>>() {});
  }

  static ExtendedAdminEvent loginEvent() {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setId("6c9d27f4-8fc1-494f-ba5b-0bd4c0bb57be");
    e.setUid("d1b2c3d4-0000-4ff4-a985-6d0000000001");
    e.setType("access.LOGIN");
    e.setTime(1788355282964L);
    e.setRealmId("b75f-realm-id");
    e.setRealmName("my-realm");
    ExtendedAuthDetails auth = new ExtendedAuthDetails(null);
    auth.setRealmId("my-realm");
    auth.setClientId("account-console");
    auth.setUserId("b2c1-user-id");
    auth.setUsername("user@domain.com");
    auth.setSessionId("eSOL_8ioeJI1addhjqlmomPO");
    auth.setIpAddress("203.0.113.45");
    e.setAuthDetails(auth);
    e.setDetails(Map.of("auth_method", "openid-connect"));
    return e;
  }

  @Test
  public void testUserEventRecord() throws Exception {
    Map<String, Object> r =
        parse(new GoogleSecOpsPayloadFormatter().format(List.of(loginEvent()), Map.of()));
    assertThat(r.keySet(), is(ENVELOPE_FIELDS));
    assertThat(r.get("uuid"), is("d1b2c3d4-0000-4ff4-a985-6d0000000001"));
    assertThat(r.get("source_timestamp"), is("2026-09-02T13:21:22.964Z"));
    assertThat(Instant.parse((String) r.get("read_timestamp")) != null, is(true));
    assertThat(r.get("object"), is("keycloak_event_entity"));
    assertThat(r.get("read_method"), is("keycloak-events-webhook"));
    assertThat(
        r.get("source_metadata"),
        is(
            Map.of(
                "schema", "keycloak",
                "table", "event_entity",
                "is_deleted", false,
                "change_type", "INSERT")));

    Map<?, ?> p = (Map<?, ?>) r.get("payload");
    assertThat(p.get("id"), is("6c9d27f4-8fc1-494f-ba5b-0bd4c0bb57be"));
    // the plain Keycloak event type, which the parser matches on (not access.LOGIN)
    assertThat(p.get("type"), is("LOGIN"));
    assertThat(p.get("realm_id"), is("b75f-realm-id"));
    assertThat(p.get("client_id"), is("account-console"));
    assertThat(p.get("user_id"), is("b2c1-user-id"));
    assertThat(p.get("session_id"), is("eSOL_8ioeJI1addhjqlmomPO"));
    assertThat(p.get("ip_address"), is("203.0.113.45"));
    assertThat(((Number) p.get("event_time")).longValue(), is(1788355282964L));
    assertThat(p.get("error"), nullValue());
    Map<String, String> details =
        JsonSerialization.readValue(
            (String) p.get("details_json"), new TypeReference<Map<String, String>>() {});
    assertThat(details, is(Map.of("auth_method", "openid-connect", "username", "user@domain.com")));
  }

  @Test
  public void testExistingUsernameDetailIsKept() throws Exception {
    ExtendedAdminEvent e = loginEvent();
    e.setDetails(Map.of("username", "typed-name"));
    Map<?, ?> p =
        (Map<?, ?>)
            new GoogleSecOpsPayloadFormatter()
                .record(new EventView(e), Instant.now())
                .get("payload");
    assertThat((String) p.get("details_json"), is("{\"username\":\"typed-name\"}"));
  }

  @Test
  public void testAdminEventRecord() throws Exception {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setId("admin-1");
    e.setType("admin.USER-CREATE");
    e.setOperationType(OperationType.CREATE);
    e.setTime(1788355282000L);
    e.setRealmId("realm-id");
    e.setResourceTypeAsString("USER");
    e.setResourcePath("users/abc");
    e.setRepresentation("{\"username\":\"new\"}");
    ExtendedAuthDetails auth = new ExtendedAuthDetails(null);
    auth.setRealmId("master");
    auth.setClientId("admin-cli");
    auth.setUserId("admin-user");
    auth.setIpAddress("10.0.0.1");
    e.setAuthDetails(auth);

    Map<String, Object> r = parse(new GoogleSecOpsPayloadFormatter().format(List.of(e), Map.of()));
    assertThat(r.get("object"), is("keycloak_admin_event_entity"));
    assertThat(r.get("source_timestamp"), is("2026-09-02T13:21:22Z"));
    assertThat(((Map<?, ?>) r.get("source_metadata")).get("table"), is("admin_event_entity"));
    Map<?, ?> p = (Map<?, ?>) r.get("payload");
    assertThat(p.get("type"), is("USER-CREATE"));
    assertThat(p.get("operation_type"), is("CREATE"));
    assertThat(p.get("resource_type"), is("USER"));
    assertThat(p.get("resource_path"), is("users/abc"));
    assertThat(p.get("auth_realm_id"), is("master"));
    assertThat(p.get("auth_client_id"), is("admin-cli"));
    assertThat(p.get("auth_user_id"), is("admin-user"));
    assertThat(p.get("ip_address"), is("10.0.0.1"));
    assertThat(p.get("representation"), is("{\"username\":\"new\"}"));
    assertThat(p.get("details_json"), is("{}"));
  }

  @Test
  public void testOneEventPerRequest() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new GoogleSecOpsPayloadFormatter()
                .format(List.of(loginEvent(), loginEvent()), Map.of()));
  }

  @Test
  public void testRetryPolicy() {
    GoogleSecOpsPayloadFormatter f = new GoogleSecOpsPayloadFormatter();
    for (int s : new int[] {400, 401, 403, 404, 413}) assertThat(f.isRetryable(s), is(false));
    for (int s : new int[] {429, 500, 503}) assertThat(f.isRetryable(s), is(true));
  }
}
