package io.phasetwo.keycloak.events;

import com.google.auto.service.AutoService;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;

@JBossLog
@AutoService(EventListenerProviderFactory.class)
public class ScriptEventListenerProviderFactory extends MultiEventListenerProviderFactory {

  public static final String PROVIDER_ID = "ext-event-script";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  protected boolean isAsync() {
    return false;
  }

  @Override
  protected EventListenerProvider configure(KeycloakSession session, Map<String, Object> config) {
    ScriptEventListenerProvider provider = new ScriptEventListenerProvider(session);
    provider.setConfig(config);
    return provider;
  }
}
