package io.phasetwo.keycloak.events;

import java.util.UUID;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerTransaction;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

/**
 * Writes Keycloak events to a JBoss logger after putting the flattened event fields into the MDC
 * under the {@code event.} prefix. Events are queued through an {@link EventListenerTransaction}
 * and only emitted after the surrounding Keycloak transaction commits, so events for requests that
 * fail downstream are dropped rather than logged. The MDC entries are scoped to each log call via
 * {@link LogContext} so they do not leak across executor threads.
 */
public class MdcLoggerEventListenerProvider implements EventListenerProvider {

  public static final String EVENT_LOGGER_NAME = "io.phasetwo.keycloak.EVENT_LOGGER";
  public static final String ADMIN_EVENT_LOGGER_NAME = "io.phasetwo.keycloak.ADMIN_EVENT_LOGGER";
  public static final String MDC_KEY_PREFIX = "event.";
  public static final String LOG_MESSAGE = "Event Logger";

  private static final Logger EVENT_LOGGER = Logger.getLogger(EVENT_LOGGER_NAME);
  private static final Logger ADMIN_EVENT_LOGGER = Logger.getLogger(ADMIN_EVENT_LOGGER_NAME);

  private final EventListenerTransaction tx;

  public MdcLoggerEventListenerProvider(KeycloakSession session) {
    this.tx = new EventListenerTransaction(this::logAdminEvent, this::logEvent);
    session.getTransactionManager().enlistAfterCompletion(tx);
  }

  @Override
  public void onEvent(Event event) {
    if (event.getId() == null) {
      event.setId(UUID.randomUUID().toString());
    }
    tx.addEvent(event);
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
    if (adminEvent.getId() == null) {
      adminEvent.setId(UUID.randomUUID().toString());
    }
    tx.addAdminEvent(adminEvent, includeRepresentation);
  }

  void logEvent(Event event) {
    FlatEvent flat = new FlatEvent(event);
    try (LogContext ctx = LogContext.with(flat, MDC_KEY_PREFIX)) {
      EVENT_LOGGER.info(LOG_MESSAGE);
    }
  }

  void logAdminEvent(AdminEvent adminEvent, boolean includeRepresentation) {
    FlatAdminEvent flat = new FlatAdminEvent(adminEvent);
    try (LogContext ctx = LogContext.with(flat, MDC_KEY_PREFIX)) {
      ADMIN_EVENT_LOGGER.info(LOG_MESSAGE);
    }
  }

  EventListenerTransaction getTransaction() {
    return tx;
  }

  @Override
  public void close() {}
}
