package io.phasetwo.keycloak.resources;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resource.RealmResourceProvider;

public class EventsResourceProvider implements RealmResourceProvider {

  private final KeycloakSession session;

  public EventsResourceProvider(KeycloakSession session) {
    this.session = session;
  }

  @Override
  public void close() {}

  @Override
  public Object getResource() {
    RealmModel realm = session.getContext().getRealm();
    EventsResource event = new EventsResource(realm);
    ResteasyProviderFactory.getInstance().injectProperties(event);
    event.setup();
    return event;
  }
}
