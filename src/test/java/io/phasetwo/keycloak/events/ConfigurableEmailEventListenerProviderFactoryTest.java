package io.phasetwo.keycloak.events;

import static io.phasetwo.keycloak.events.ConfigurableEmailEventListenerProviderFactory.parseIncludedEvents;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventType;

public class ConfigurableEmailEventListenerProviderFactoryTest {

  private static final ImmutableSet<EventType> DEFAULTS =
      ImmutableSet.of(
          EventType.LOGIN_ERROR, EventType.UPDATE_CREDENTIAL, EventType.REMOVE_CREDENTIAL);

  @Test
  public void missingOrBlankUsesDefaults() {
    assertThat(parseIncludedEvents(null), equalTo(DEFAULTS));
    assertThat(parseIncludedEvents(""), equalTo(DEFAULTS));
    assertThat(parseIncludedEvents("   "), equalTo(DEFAULTS));
  }

  @Test
  public void parsesConfiguredEvents() {
    assertThat(
        parseIncludedEvents("LOGIN_ERROR##user_disabled_by_permanent_lockout"),
        equalTo(
            ImmutableSet.of(EventType.LOGIN_ERROR, EventType.USER_DISABLED_BY_PERMANENT_LOCKOUT)));
  }

  @Test
  public void skipsMalformedEntries() {
    assertThat(
        parseIncludedEvents(" LOGIN_ERROR ## ##NOT_AN_EVENT##UPDATE_CREDENTIAL##"),
        equalTo(ImmutableSet.of(EventType.LOGIN_ERROR, EventType.UPDATE_CREDENTIAL)));
  }
}
