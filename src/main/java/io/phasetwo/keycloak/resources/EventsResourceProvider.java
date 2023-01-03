package io.phasetwo.keycloak.resources;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public class EventsResourceProvider extends BaseRealmResourceProvider {

  public EventsResourceProvider(KeycloakSession session) {
    super(session);
  }

  @Override
  public Object getRealmResource() {
    RealmModel realm = session.getContext().getRealm();
    EventsResource event = new EventsResource(realm);
    ResteasyProviderFactory.getInstance().injectProperties(event);
    event.setup();
    return event;
  }
}
