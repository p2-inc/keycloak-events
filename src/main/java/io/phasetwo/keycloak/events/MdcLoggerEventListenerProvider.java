package io.phasetwo.keycloak.events;

import java.util.UUID;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

/**
 * Writes Keycloak events to a JBoss logger after putting the flattened event fields into the MDC
 * under the {@code event.} prefix. The MDC entries are scoped to the log call via {@link
 * LogContext} so they do not leak across executor threads.
 */
public class MdcLoggerEventListenerProvider implements EventListenerProvider {

  public static final String EVENT_LOGGER_NAME = "io.phasetwo.keycloak.EVENT_LOGGER";
  public static final String ADMIN_EVENT_LOGGER_NAME = "io.phasetwo.keycloak.ADMIN_EVENT_LOGGER";
  public static final String MDC_KEY_PREFIX = "event.";
  public static final String LOG_MESSAGE = "Event Logger";

  private static final Logger EVENT_LOGGER = Logger.getLogger(EVENT_LOGGER_NAME);
  private static final Logger ADMIN_EVENT_LOGGER = Logger.getLogger(ADMIN_EVENT_LOGGER_NAME);

  @Override
  public void onEvent(Event event) {
    if (event.getId() == null) {
      event.setId(UUID.randomUUID().toString());
    }
    FlatEvent flat = new FlatEvent(event);
    try (LogContext ctx = LogContext.with(flat, MDC_KEY_PREFIX)) {
      EVENT_LOGGER.info(LOG_MESSAGE);
    }
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
    if (adminEvent.getId() == null) {
      adminEvent.setId(UUID.randomUUID().toString());
    }
    FlatAdminEvent flat = new FlatAdminEvent(adminEvent);
    try (LogContext ctx = LogContext.with(flat, MDC_KEY_PREFIX)) {
      ADMIN_EVENT_LOGGER.info(LOG_MESSAGE);
    }
  }

  @Override
  public void close() {}
}
