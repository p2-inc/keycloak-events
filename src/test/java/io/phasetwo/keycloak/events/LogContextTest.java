package io.phasetwo.keycloak.events;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;

public class LogContextTest {

  @BeforeEach
  @AfterEach
  public void clearMdc() {
    MDC.clear();
  }

  @Test
  public void appliesPrefixedKeysAndRemovesThemOnClose() {
    try (LogContext ctx =
        LogContext.with(ImmutableMap.of("realmId", "r", "userId", "u"), "event.")) {
      assertThat(MDC.get("event.realmId"), equalTo("r"));
      assertThat(MDC.get("event.userId"), equalTo("u"));
    }
    assertThat(MDC.get("event.realmId"), is(nullValue()));
    assertThat(MDC.get("event.userId"), is(nullValue()));
  }

  @Test
  public void restoresPreexistingValuesOnClose() {
    MDC.put("event.realmId", "preexisting");
    try (LogContext ctx = LogContext.with(ImmutableMap.of("realmId", "new"), "event.")) {
      assertThat(MDC.get("event.realmId"), equalTo("new"));
    }
    assertThat(MDC.get("event.realmId"), equalTo("preexisting"));
  }

  @Test
  public void emptyPrefixWorks() {
    try (LogContext ctx = LogContext.with(ImmutableMap.of("foo", "bar"), "")) {
      assertThat(MDC.get("foo"), equalTo("bar"));
    }
    assertThat(MDC.get("foo"), is(nullValue()));
  }

  @Test
  public void nullPrefixIsTreatedAsEmpty() {
    try (LogContext ctx = LogContext.with(ImmutableMap.of("foo", "bar"), null)) {
      assertThat(MDC.get("foo"), equalTo("bar"));
    }
    assertThat(MDC.get("foo"), is(nullValue()));
  }

  @Test
  public void flatEventOverloadAppliesPrefix() {
    Event event = new Event();
    event.setRealmId("r");
    event.setUserId("u");
    event.setType(EventType.LOGIN);

    try (LogContext ctx = LogContext.with(new FlatEvent(event), "event.")) {
      assertThat(MDC.get("event.class"), equalTo(FlatEvent.CLASS));
      assertThat(MDC.get("event.realmId"), equalTo("r"));
      assertThat(MDC.get("event.userId"), equalTo("u"));
      assertThat(MDC.get("event.type"), equalTo("LOGIN"));
    }
    assertThat(MDC.get("event.class"), is(nullValue()));
    assertThat(MDC.get("event.realmId"), is(nullValue()));
    assertThat(MDC.get("event.userId"), is(nullValue()));
    assertThat(MDC.get("event.type"), is(nullValue()));
  }

  @Test
  public void cleansUpEvenWhenBodyThrows() {
    RuntimeException raised = null;
    try (LogContext ctx = LogContext.with(ImmutableMap.of("foo", "bar"), "event.")) {
      throw new RuntimeException("boom");
    } catch (RuntimeException e) {
      raised = e;
    }
    assertThat(raised, is(notNullValue()));
    assertThat(MDC.get("event.foo"), is(nullValue()));
  }
}
