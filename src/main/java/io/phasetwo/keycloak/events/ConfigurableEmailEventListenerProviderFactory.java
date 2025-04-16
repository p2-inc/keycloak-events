package io.phasetwo.keycloak.events;

import io.phasetwo.keycloak.config.ConfigurationAware;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.events.email.EmailEventListenerProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

public class ConfigurableEmailEventListenerProviderFactory implements EventListenerProviderFactory {

  private static final Set<EventType> SUPPORTED_EVENTS = new HashSet<>();

  public static final String PROVIDER_ID = "ext-event-email";

  static {
    Collections.addAll(
        SUPPORTED_EVENTS,
        EventType.LOGIN_ERROR,
        EventType.UPDATE_PASSWORD,
        EventType.REMOVE_TOTP,
        EventType.UPDATE_TOTP,
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
      String inc = realm.getAttribute(INCLUDED_EVENTS_KEY);
      if (inc != null) {
        String[] include = inc.split("##");
        if (include != null) {
          for (String i : include) {
            includedEvents.add(EventType.valueOf(i.toUpperCase()));
          }
        } else {
          includedEvents.addAll(SUPPORTED_EVENTS);
        }
      }
    }
    return new EmailEventListenerProvider(session, includedEvents);
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
