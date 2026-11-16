package io.phasetwo.keycloak.events.format;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.OperationType;

public class EventViewTest {

  @Test
  public void testUserEvent() {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setType("access.LOGIN_ERROR");
    e.setTime(1700000000123L);
    e.setError("invalid_user_credentials");
    ExtendedAuthDetails auth = new ExtendedAuthDetails(null);
    auth.setUserId("u1");
    auth.setIpAddress("203.0.113.5");
    e.setAuthDetails(auth);
    e.setDetails(Map.of("username", "jdoe"));

    EventView v = EventView.of(e);
    assertThat(v.isUserEvent(), is(true));
    assertThat(v.isAdminEvent(), is(false));
    assertThat(v.category(), is("user"));
    assertThat(v.nativeType(), is("LOGIN_ERROR"));
    assertThat(v.username(), is("jdoe"));
    assertThat(v.isError(), is(true));
    assertThat(v.timestamp().toString(), is("2023-11-14T22:13:20.123Z"));
  }

  @Test
  public void testAdminEvent() {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setType("admin.USER-CREATE");
    e.setOperationType(OperationType.CREATE);
    EventView v = EventView.of(e);
    assertThat(v.isAdminEvent(), is(true));
    assertThat(v.category(), is("admin"));
    assertThat(v.nativeType(), is("CREATE"));
    assertThat(v.userId() == null, is(true));
    assertThat(v.isError(), is(false));
  }

  @Test
  public void testCustomEvent() {
    ExtendedAdminEvent e = new ExtendedAdminEvent();
    e.setType("system.ORG-CREATE");
    EventView v = EventView.of(e);
    assertThat(v.category(), is("system"));
    assertThat(v.nativeType(), is("ORG-CREATE"));
  }
}
