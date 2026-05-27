package io.phasetwo.keycloak.events;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.util.JsonSerialization;

public class FlatAdminEventTest {

  @Test
  public void fullyPopulatedAdminEventMapsAllFields() throws Exception {
    AdminEvent event = new AdminEvent();
    event.setId("admin-event-id");
    event.setTime(1700000000000L);
    event.setRealmId("realm-uuid");
    event.setRealmName("acm");
    event.setOperationType(OperationType.CREATE);
    event.setResourceType(ResourceType.USER);
    event.setResourcePath("users/abc");
    event.setRepresentation("{\"username\":\"alice\"}");
    event.setError("some_error");
    event.setDetails(ImmutableMap.of("k", "v"));

    AuthDetails auth = new AuthDetails();
    auth.setRealmId("auth-realm-uuid");
    auth.setRealmName("master");
    auth.setClientId("admin-cli");
    auth.setUserId("admin-user-uuid");
    auth.setIpAddress("10.0.0.2");
    event.setAuthDetails(auth);

    Map<String, Object> map = new FlatAdminEvent(event).toMap();

    assertThat(map.get("class"), equalTo(FlatAdminEvent.CLASS));
    assertThat(map.get("id"), equalTo("admin-event-id"));
    assertThat(map.get("time"), equalTo(1700000000000L));
    assertThat(map.get("realmId"), equalTo("realm-uuid"));
    assertThat(map.get("realmName"), equalTo("acm"));
    assertThat(map.get("operationType"), equalTo("CREATE"));
    assertThat(map.get("resourceType"), equalTo("USER"));
    assertThat(map.get("resourcePath"), equalTo("users/abc"));
    assertThat(map.get("representation"), equalTo("{\"username\":\"alice\"}"));
    assertThat(map.get("error"), equalTo("some_error"));
    assertThat(map.get("authRealmId"), equalTo("auth-realm-uuid"));
    assertThat(map.get("authRealmName"), equalTo("master"));
    assertThat(map.get("authClientId"), equalTo("admin-cli"));
    assertThat(map.get("authUserId"), equalTo("admin-user-uuid"));
    assertThat(map.get("authIpAddress"), equalTo("10.0.0.2"));

    Map<String, String> details =
        JsonSerialization.readValue((String) map.get("detailsJson"), Map.class);
    assertThat(details.get("k"), equalTo("v"));
  }

  @Test
  public void nullAuthDetailsOmitsAllAuthKeys() {
    AdminEvent event = new AdminEvent();
    event.setTime(1L);
    event.setAuthDetails(null);

    Map<String, Object> map = new FlatAdminEvent(event).toMap();

    assertThat(map.get("class"), equalTo(FlatAdminEvent.CLASS));
    assertThat(map.containsKey("authRealmId"), is(false));
    assertThat(map.containsKey("authRealmName"), is(false));
    assertThat(map.containsKey("authClientId"), is(false));
    assertThat(map.containsKey("authUserId"), is(false));
    assertThat(map.containsKey("authIpAddress"), is(false));
  }

  @Test
  public void omitsNullsButKeepsClassAndTime() {
    AdminEvent event = new AdminEvent();
    event.setTime(42L);

    Map<String, Object> map = new FlatAdminEvent(event).toMap();

    assertThat(map.get("class"), equalTo(FlatAdminEvent.CLASS));
    assertThat(map.get("time"), equalTo(42L));
    assertThat(map.containsKey("id"), is(false));
    assertThat(map.containsKey("operationType"), is(false));
    assertThat(map.containsKey("resourceType"), is(false));
    assertThat(map.containsKey("resourcePath"), is(false));
    assertThat(map.containsKey("representation"), is(false));
    assertThat(map.containsKey("error"), is(false));
    assertThat(map.containsKey("realmId"), is(false));
    assertThat(map.containsKey("realmName"), is(false));
    assertThat(map.containsKey("detailsJson"), is(false));
  }

  @Test
  public void enumsAreSerializedByName() {
    AdminEvent event = new AdminEvent();
    event.setOperationType(OperationType.DELETE);
    event.setResourceType(ResourceType.CLIENT);

    Map<String, Object> map = new FlatAdminEvent(event).toMap();
    assertThat(map.get("operationType"), equalTo("DELETE"));
    assertThat(map.get("resourceType"), equalTo("CLIENT"));
  }
}
