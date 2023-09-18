package io.phasetwo.keycloak.resources;

import io.phasetwo.keycloak.ext.resource.BaseRealmResourceProvider;
import org.keycloak.models.KeycloakSession;

public class EventsResourceProvider extends BaseRealmResourceProvider {

  public EventsResourceProvider(KeycloakSession session) {
    super(session);
  }

  @Override
  public Object getRealmResource() {
    EventsResource event = new EventsResource(session);
    event.setup();
    return event;
  }
}
