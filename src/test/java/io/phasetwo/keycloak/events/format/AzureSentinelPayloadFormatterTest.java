package io.phasetwo.keycloak.events.format;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.fasterxml.jackson.core.type.TypeReference;
import io.phasetwo.keycloak.events.format.AzureSentinelPayloadFormatter.Column;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.OperationType;
import org.keycloak.util.JsonSerialization;

public class AzureSentinelPayloadFormatterTest {

  /** Log Analytics column names: start with a letter, letters/digits/underscores, at most 45. */
  static final Pattern COLUMN_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,44}$");

  /** Column names Log Analytics reserves. */
  static final Set<String> RESERVED =
      Set.of("_ResourceId", "id", "_SubscriptionId", "TenantId", "Type", "UniqueId", "Title");

  static final Set<String> TYPES =
      Set.of("string", "datetime", "dynamic", "int", "long", "real", "boolean");

  /**
   * Check rows against the declared stream columns: only declared columns, with values of the
   * declared type. Shared with the integration test.
   */
  public static void assertRowsMatchStream(List<Map<String, Object>> rows) {
    Map<String, String> types =
        AzureSentinelPayloadFormatter.COLUMNS.stream()
            .collect(Collectors.toMap(Column::name, Column::type));
    for (Map<String, Object> row : rows) {
      assertThat(row, hasKey("TimeGenerated"));
      for (Map.Entry<String, Object> e : row.entrySet()) {
        String type = types.get(e.getKey());
        assertThat("undeclared column " + e.getKey(), type != null, is(true));
        Object v = e.getValue();
        switch (type) {
          case "string" -> assertThat(e.getKey(), v instanceof String, is(true));
          case "datetime" -> Instant.parse((String) v);
          case "dynamic" -> assertThat(e.getKey(), v instanceof Map || v instanceof List, is(true));
          default -> throw new AssertionError("unexpected type " + type);
        }
      }
    }
  }

  static List<Map<String, Object>> rows(FormattedPayload p) throws Exception {
    return JsonSerialization.readValue(p.body(), new TypeReference<List<Map<String, Object>>>() {});
  }

  @Test
  public void testColumnsAreValidLogAnalyticsColumns() {
    for (Column c : AzureSentinelPayloadFormatter.COLUMNS) {
      assertThat(c.name(), COLUMN_NAME.matcher(c.name()).matches(), is(true));
      assertThat(c.name(), RESERVED.contains(c.name()), is(false));
      assertThat(c.name(), TYPES.contains(c.type()), is(true));
    }
    assertThat(
        AzureSentinelPayloadFormatter.COLUMNS.get(0), is(new Column("TimeGenerated", "datetime")));
  }

  @Test
  public void testUserEventRow() throws Exception {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setId("ev-1");
    e.setUid("uid-1");
    e.setType("access.LOGIN_ERROR");
    e.setTime(1788355282964L);
    e.setRealmId("realm-id");
    e.setRealmName("my-realm");
    e.setError("invalid_user_credentials");
    ExtendedAuthDetails auth = new ExtendedAuthDetails(null);
    auth.setClientId("account-console");
    auth.setUserId("user-1");
    auth.setIpAddress("203.0.113.45");
    e.setAuthDetails(auth);
    e.setDetails(Map.of("username", "jdoe", "auth_method", "openid-connect"));

    FormattedPayload p = new AzureSentinelPayloadFormatter().format(List.of(e), Map.of());
    assertThat(p.contentType(), is("application/json"));
    List<Map<String, Object>> rows = rows(p);
    assertRowsMatchStream(rows);
    Map<String, Object> r = rows.get(0);
    assertThat(r.get("TimeGenerated"), is("2026-09-02T13:21:22.964Z"));
    assertThat(r.get("EventId"), is("ev-1"));
    assertThat(r.get("EventUid"), is("uid-1"));
    assertThat(r.get("EventCategory"), is("user"));
    assertThat(r.get("EventType"), is("LOGIN_ERROR"));
    assertThat(r.get("EventTypeFull"), is("access.LOGIN_ERROR"));
    assertThat(r.get("EventResult"), is("Failure"));
    assertThat(r.get("RealmName"), is("my-realm"));
    assertThat(r.get("ClientId"), is("account-console"));
    assertThat(r.get("UserId"), is("user-1"));
    assertThat(r.get("Username"), is("jdoe"));
    assertThat(r.get("IpAddress"), is("203.0.113.45"));
    assertThat(r.get("Error"), is("invalid_user_credentials"));
    assertThat(r.get("Details"), is(Map.of("username", "jdoe", "auth_method", "openid-connect")));
    assertThat(r, not(hasKey("OperationType")));
    assertThat(r, not(hasKey("TargetUsername")));
  }

  @Test
  public void testAdminEventRowTruncatesRepresentation() throws Exception {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setId("ev-2");
    e.setType("admin.USER-UPDATE");
    e.setOperationType(OperationType.UPDATE);
    e.setResourceTypeAsString("USER");
    e.setResourcePath("users/u1");
    e.setRepresentation("x".repeat(100_000));
    e.setDetails(Map.of("userId", "target-id", "username", "target"));
    ExtendedAuthDetails auth = new ExtendedAuthDetails(null);
    auth.setRealmId("master");
    auth.setUserId("admin-1");
    e.setAuthDetails(auth);

    List<Map<String, Object>> rows =
        rows(new AzureSentinelPayloadFormatter().format(List.of(e), Map.of()));
    assertRowsMatchStream(rows);
    Map<String, Object> r = rows.get(0);
    assertThat(r.get("EventCategory"), is("admin"));
    assertThat(r.get("EventType"), is("UPDATE"));
    assertThat(r.get("OperationType"), is("UPDATE"));
    assertThat(r.get("ResourceType"), is("USER"));
    assertThat(r.get("ResourcePath"), is("users/u1"));
    assertThat(r.get("AuthRealm"), is("master"));
    assertThat(r.get("UserId"), is("admin-1"));
    assertThat(r.get("TargetUserId"), is("target-id"));
    assertThat(r.get("TargetUsername"), is("target"));
    assertThat(r.get("EventResult"), is("Success"));
    assertThat(
        ((String) r.get("Representation")).length(),
        is(AzureSentinelPayloadFormatter.MAX_FIELD_CHARS));
  }

  @Test
  public void testStreamColumnsJson() throws Exception {
    List<Map<String, String>> cols =
        JsonSerialization.readValue(
            AzureSentinelPayloadFormatter.streamColumnsJson(),
            new TypeReference<List<Map<String, String>>>() {});
    assertThat(cols.size(), is(AzureSentinelPayloadFormatter.COLUMNS.size()));
    assertThat(cols.get(0), is(Map.of("name", "TimeGenerated", "type", "datetime")));
  }

  @Test
  public void testRetryPolicy() {
    AzureSentinelPayloadFormatter f = new AzureSentinelPayloadFormatter();
    for (int s : new int[] {400, 403, 404, 413}) assertThat(f.isRetryable(s), is(false));
    for (int s : new int[] {401, 429, 500, 503}) assertThat(f.isRetryable(s), is(true));
  }
}
