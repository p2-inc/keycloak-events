package io.phasetwo.keycloak.events;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.config.ConfigurationAware;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.events.email.EmailEventListenerProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

/**
 * Sends emails to the user for the event types listed in the realm attribute
 * `_providerConfig.ext-event-email.includedEvents` (separated by `##`). If the attribute is missing
 * or empty, the default set of supported events is used. Emails are only sent to users with an
 * email address that has been verified (enforced by {@link EmailEventListenerProvider}).
 */
@JBossLog
@AutoService(EventListenerProviderFactory.class)
public class ConfigurableEmailEventListenerProviderFactory implements EventListenerProviderFactory {

  private static final Set<EventType> SUPPORTED_EVENTS = new HashSet<>();

  public static final String PROVIDER_ID = "ext-event-email";

  /*
    removed per https://www.keycloak.org/docs/latest/upgrading/index.html#new-generalized-event-types-for-credentials
    EventType.UPDATE_PASSWORD,
    EventType.REMOVE_TOTP,
    EventType.UPDATE_TOTP,
  */
  static {
    Collections.addAll(
        SUPPORTED_EVENTS,
        EventType.LOGIN_ERROR,
        EventType.UPDATE_CREDENTIAL,
        EventType.REMOVE_CREDENTIAL);
  }

  private static final String INCLUDED_EVENTS_KEY =
      "_providerConfig.ext-event-email.includedEvents";

  @Override
  public EventListenerProvider create(KeycloakSession session) {
    Set<EventType> includedEvents = new HashSet<>();
    RealmModel realm = ConfigurationAware.getRealm(session);
    if (realm != null) {
      includedEvents = parseIncludedEvents(realm.getAttribute(INCLUDED_EVENTS_KEY));
    }
    return new EmailEventListenerProvider(session, includedEvents);
  }

  static Set<EventType> parseIncludedEvents(String inc) {
    Set<EventType> includedEvents = new HashSet<>();
    if (inc == null || inc.isBlank()) {
      includedEvents.addAll(SUPPORTED_EVENTS);
      return includedEvents;
    }
    for (String i : inc.split("##")) {
      String type = i.trim();
      if (type.isEmpty()) continue;
      try {
        includedEvents.add(EventType.valueOf(type.toUpperCase()));
      } catch (IllegalArgumentException e) {
        log.warnf("Skipping unknown event type %s in %s", type, INCLUDED_EVENTS_KEY);
      }
    }
    return includedEvents;
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return PROVIDER_ID;
  }
}
