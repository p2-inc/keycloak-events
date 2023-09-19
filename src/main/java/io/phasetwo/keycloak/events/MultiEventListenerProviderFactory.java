package io.phasetwo.keycloak.events;

import io.phasetwo.keycloak.ext.config.ConfigurationAware;
import io.phasetwo.keycloak.ext.event.AbstractEventListenerProviderFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.executors.ExecutorsProvider;
import org.keycloak.models.KeycloakSession;

@JBossLog
public abstract class MultiEventListenerProviderFactory extends AbstractEventListenerProviderFactory
    implements ConfigurationAware {

  @Override
  public MultiEventListenerProvider create(KeycloakSession session) {
    try {
      ExecutorService exec =
          session.getProvider(ExecutorsProvider.class).getExecutor("multi-event-provider-threads");
      List<EventListenerProvider> providers =
          getConfigurations(session).stream()
              .map(config -> configure(session, config))
              .collect(Collectors.toList());
      return new MultiEventListenerProvider(session, providers, isAsync(), exec);
    } catch (Exception e) {
      log.warn("Error configuring provider", e);
      throw new IllegalStateException(e);
    }
  }

  /**
   * To implement an event listener that can run multiple instances with a configuration, you must
   * extend this class and implement this method instead of create()
   */
  protected abstract EventListenerProvider configure(
      KeycloakSession session, Map<String, Object> config);

  /**
   * Override this if you want the event listeners to run asynchronously. Note that this means it
   * will not execute immediately, and the event may be lost.
   */
  protected boolean isAsync() {
    return false;
  }
}
