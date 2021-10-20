package io.phasetwo.keycloak.events;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSessionFactory;

/** */
public abstract class AbstractEventListenerProviderFactory implements EventListenerProviderFactory {

  @Override
  public void init(Config.Scope scope) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}
