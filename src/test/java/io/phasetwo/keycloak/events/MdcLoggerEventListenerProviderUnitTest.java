package io.phasetwo.keycloak.events;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.slf4j.LoggerFactory;

public class MdcLoggerEventListenerProviderUnitTest {

  private MdcLoggerEventListenerProvider provider;
  private ListAppender<ILoggingEvent> eventAppender;
  private ListAppender<ILoggingEvent> adminAppender;
  private Logger eventLogger;
  private Logger adminLogger;

  @BeforeEach
  public void setUp() {
    MDC.clear();
    provider = new MdcLoggerEventListenerProvider();
    eventLogger =
        (Logger) LoggerFactory.getLogger(MdcLoggerEventListenerProvider.EVENT_LOGGER_NAME);
    adminLogger =
        (Logger) LoggerFactory.getLogger(MdcLoggerEventListenerProvider.ADMIN_EVENT_LOGGER_NAME);
    eventLogger.setLevel(Level.INFO);
    adminLogger.setLevel(Level.INFO);
    eventAppender = new ListAppender<>();
    eventAppender.start();
    adminAppender = new ListAppender<>();
    adminAppender.start();
    eventLogger.addAppender(eventAppender);
    adminLogger.addAppender(adminAppender);
  }

  @AfterEach
  public void tearDown() {
    eventLogger.detachAppender(eventAppender);
    adminLogger.detachAppender(adminAppender);
    MDC.clear();
  }

  @Test
  public void userEventEmitsToEventLoggerWithExpectedMdc() {
    Event event = new Event();
    event.setId("event-id");
    event.setType(EventType.LOGIN_ERROR);
    event.setRealmId("realm-uuid");
    event.setClientId("dashboard");
    event.setUserId("user-uuid");
    event.setTime(1700000000000L);
    event.setDetails(ImmutableMap.of("username", "alice"));

    provider.onEvent(event);

    assertThat(eventAppender.list.size(), is(1));
    ILoggingEvent logged = eventAppender.list.get(0);
    assertThat(logged.getLevel(), equalTo(Level.INFO));
    assertThat(logged.getMessage(), equalTo(MdcLoggerEventListenerProvider.LOG_MESSAGE));
    assertThat(logged.getLoggerName(), equalTo(MdcLoggerEventListenerProvider.EVENT_LOGGER_NAME));

    Map<String, String> mdc = logged.getMDCPropertyMap();
    assertThat(mdc.get("event.class"), equalTo(FlatEvent.CLASS));
    assertThat(mdc.get("event.id"), equalTo("event-id"));
    assertThat(mdc.get("event.type"), equalTo("LOGIN_ERROR"));
    assertThat(mdc.get("event.realmId"), equalTo("realm-uuid"));
    assertThat(mdc.get("event.clientId"), equalTo("dashboard"));
    assertThat(mdc.get("event.userId"), equalTo("user-uuid"));
    assertThat(mdc.get("event.time"), equalTo("1700000000000"));
    assertThat(mdc.get("event.detailsJson"), is(notNullValue()));
  }

  @Test
  public void adminEventEmitsToAdminLoggerWithExpectedMdc() {
    AdminEvent event = new AdminEvent();
    event.setId("admin-event-id");
    event.setTime(1700000000000L);
    event.setRealmId("realm-uuid");
    event.setOperationType(OperationType.CREATE);
    event.setResourceType(ResourceType.USER);
    event.setResourcePath("users/abc");

    AuthDetails auth = new AuthDetails();
    auth.setRealmId("master");
    auth.setClientId("admin-cli");
    auth.setUserId("admin-user");
    event.setAuthDetails(auth);

    provider.onEvent(event, false);

    assertThat(adminAppender.list.size(), is(1));
    ILoggingEvent logged = adminAppender.list.get(0);
    assertThat(logged.getLevel(), equalTo(Level.INFO));
    assertThat(logged.getMessage(), equalTo(MdcLoggerEventListenerProvider.LOG_MESSAGE));
    assertThat(
        logged.getLoggerName(), equalTo(MdcLoggerEventListenerProvider.ADMIN_EVENT_LOGGER_NAME));

    Map<String, String> mdc = logged.getMDCPropertyMap();
    assertThat(mdc.get("event.class"), equalTo(FlatAdminEvent.CLASS));
    assertThat(mdc.get("event.id"), equalTo("admin-event-id"));
    assertThat(mdc.get("event.operationType"), equalTo("CREATE"));
    assertThat(mdc.get("event.resourceType"), equalTo("USER"));
    assertThat(mdc.get("event.resourcePath"), equalTo("users/abc"));
    assertThat(mdc.get("event.authRealmId"), equalTo("master"));
    assertThat(mdc.get("event.authClientId"), equalTo("admin-cli"));
    assertThat(mdc.get("event.authUserId"), equalTo("admin-user"));
  }

  @Test
  public void missingUserEventIdIsAutoGenerated() {
    Event event = new Event();
    event.setType(EventType.LOGIN);
    assertThat(event.getId(), is(nullValue()));

    provider.onEvent(event);

    assertThat(event.getId(), is(notNullValue()));
    UUID.fromString(event.getId()); // throws if not a valid UUID
    assertThat(
        eventAppender.list.get(0).getMDCPropertyMap().get("event.id"), equalTo(event.getId()));
  }

  @Test
  public void missingAdminEventIdIsAutoGenerated() {
    AdminEvent event = new AdminEvent();
    event.setOperationType(OperationType.UPDATE);
    assertThat(event.getId(), is(nullValue()));

    provider.onEvent(event, false);

    assertThat(event.getId(), is(notNullValue()));
    UUID.fromString(event.getId());
    assertThat(
        adminAppender.list.get(0).getMDCPropertyMap().get("event.id"), equalTo(event.getId()));
  }

  @Test
  public void presetUserEventIdIsPreserved() {
    Event event = new Event();
    event.setId("explicit-id");
    event.setType(EventType.LOGIN);

    provider.onEvent(event);

    assertThat(event.getId(), equalTo("explicit-id"));
  }

  @Test
  public void mdcIsClearedAfterUserEvent() {
    Event event = new Event();
    event.setType(EventType.LOGIN);
    event.setRealmId("r");
    event.setUserId("u");

    provider.onEvent(event);

    assertThat(MDC.get("event.class"), is(nullValue()));
    assertThat(MDC.get("event.type"), is(nullValue()));
    assertThat(MDC.get("event.realmId"), is(nullValue()));
    assertThat(MDC.get("event.userId"), is(nullValue()));
    assertThat(MDC.get("event.id"), is(nullValue()));
  }

  @Test
  public void mdcIsClearedAfterAdminEvent() {
    AdminEvent event = new AdminEvent();
    event.setOperationType(OperationType.DELETE);
    event.setResourceType(ResourceType.CLIENT);

    provider.onEvent(event, true);

    assertThat(MDC.get("event.class"), is(nullValue()));
    assertThat(MDC.get("event.operationType"), is(nullValue()));
    assertThat(MDC.get("event.resourceType"), is(nullValue()));
  }

  @Test
  public void preexistingMdcOutsidePrefixSurvives() {
    MDC.put("realm", "external");
    Event event = new Event();
    event.setType(EventType.LOGIN);

    provider.onEvent(event);

    assertThat(MDC.get("realm"), equalTo("external"));
  }

  @Test
  public void nullValuesAreOmittedFromMdc() {
    Event event = new Event();
    event.setType(EventType.LOGIN);
    // realmId, clientId, userId, sessionId, ipAddress, error, details all left null

    provider.onEvent(event);

    Map<String, String> mdc = eventAppender.list.get(0).getMDCPropertyMap();
    assertThat(mdc.containsKey("event.realmId"), is(false));
    assertThat(mdc.containsKey("event.clientId"), is(false));
    assertThat(mdc.containsKey("event.userId"), is(false));
    assertThat(mdc.containsKey("event.sessionId"), is(false));
    assertThat(mdc.containsKey("event.ipAddress"), is(false));
    assertThat(mdc.containsKey("event.error"), is(false));
    assertThat(mdc.containsKey("event.detailsJson"), is(false));
  }

  @Test
  public void everyInvocationProducesExactlyOneLogRecord() {
    Event event = new Event();
    event.setType(EventType.LOGIN);
    int n = 5;
    for (int i = 0; i < n; i++) {
      provider.onEvent(event);
    }
    assertThat((long) eventAppender.list.size(), greaterThanOrEqualTo((long) n));
    assertThat(eventAppender.list.size(), equalTo(n));
  }
}
