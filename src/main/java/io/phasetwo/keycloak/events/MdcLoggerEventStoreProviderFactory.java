package io.phasetwo.keycloak.events;

import com.google.auto.service.AutoService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.EventStoreProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

@JBossLog
@AutoService(EventStoreProviderFactory.class)
public class MdcLoggerEventStoreProviderFactory implements EventStoreProviderFactory {

  public static final String PROVIDER_ID = "ext-event-mdc-logger-store";

  private boolean useJpa = false;

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public EventStoreProvider create(KeycloakSession session) {
    return new MdcLoggerEventStoreProvider(session, useJpa);
  }

  @Override
  public void init(Config.Scope scope) {
    useJpa = scope.getBoolean("useJpa", false);
    log.infof("useJpa %b", useJpa);
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}
