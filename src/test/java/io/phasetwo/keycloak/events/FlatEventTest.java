package io.phasetwo.keycloak.events;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.util.JsonSerialization;

public class FlatEventTest {

  @Test
  public void fullyPopulatedEventMapsAllFields() throws Exception {
    Event event = new Event();
    event.setId("event-id");
    event.setType(EventType.LOGIN_ERROR);
    event.setRealmId("realm-uuid");
    event.setRealmName("acm");
    event.setClientId("dashboard");
    event.setUserId("user-uuid");
    event.setSessionId("session-uuid");
    event.setIpAddress("10.0.0.1");
    event.setError("invalid_user_credentials");
    event.setTime(1700000000000L);
    event.setDetails(ImmutableMap.of("auth_method", "openid-connect", "username", "alice"));

    FlatEvent flat = new FlatEvent(event);
    Map<String, Object> map = flat.toMap();

    assertThat(map.get("class"), equalTo(FlatEvent.CLASS));
    assertThat(map.get("id"), equalTo("event-id"));
    assertThat(map.get("type"), equalTo("LOGIN_ERROR"));
    assertThat(map.get("realmId"), equalTo("realm-uuid"));
    assertThat(map.get("realmName"), equalTo("acm"));
    assertThat(map.get("clientId"), equalTo("dashboard"));
    assertThat(map.get("userId"), equalTo("user-uuid"));
    assertThat(map.get("sessionId"), equalTo("session-uuid"));
    assertThat(map.get("ipAddress"), equalTo("10.0.0.1"));
    assertThat(map.get("error"), equalTo("invalid_user_credentials"));
    assertThat(map.get("time"), equalTo(1700000000000L));

    Map<String, String> details =
        JsonSerialization.readValue((String) map.get("detailsJson"), Map.class);
    assertThat(details.get("auth_method"), equalTo("openid-connect"));
    assertThat(details.get("username"), equalTo("alice"));
  }

  @Test
  public void omitsNullsButKeepsClassAndTime() {
    Event event = new Event();
    event.setTime(42L);

    Map<String, Object> map = new FlatEvent(event).toMap();

    assertThat(map.get("class"), equalTo(FlatEvent.CLASS));
    assertThat(map.get("time"), equalTo(42L));
    assertThat(map.containsKey("id"), is(false));
    assertThat(map.containsKey("type"), is(false));
    assertThat(map.containsKey("realmId"), is(false));
    assertThat(map.containsKey("clientId"), is(false));
    assertThat(map.containsKey("userId"), is(false));
    assertThat(map.containsKey("sessionId"), is(false));
    assertThat(map.containsKey("ipAddress"), is(false));
    assertThat(map.containsKey("error"), is(false));
    assertThat(map.containsKey("detailsJson"), is(false));
  }

  @Test
  public void nullDetailsProducesNoDetailsJsonEntry() {
    Event event = new Event();
    event.setId("x");
    event.setDetails(null);

    Map<String, Object> map = new FlatEvent(event).toMap();

    assertThat(map.containsKey("detailsJson"), is(false));
  }

  @Test
  public void typeEnumIsSerializedByName() {
    Event event = new Event();
    event.setType(EventType.REGISTER);

    assertThat(new FlatEvent(event).toMap().get("type"), equalTo("REGISTER"));
  }

  @Test
  public void nullTypeIsOmitted() {
    Event event = new Event();
    assertThat(new FlatEvent(event).getType(), is(nullValue()));
    assertThat(new FlatEvent(event).toMap().containsKey("type"), is(false));
  }

  @Test
  public void mapKeysAreCamelCaseForMdcUse() {
    Event event = new Event();
    event.setRealmId("r");
    event.setClientId("c");
    event.setUserId("u");
    event.setSessionId("s");
    event.setIpAddress("1.2.3.4");

    Map<String, Object> map = new FlatEvent(event).toMap();
    assertThat(map.keySet(), hasItems("realmId", "clientId", "userId", "sessionId", "ipAddress"));
    assertThat(map.containsKey("realmid"), is(not(true)));
    assertThat(map.containsKey("clientid"), is(not(true)));
  }
}
