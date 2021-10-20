package io.phasetwo.keycloak.events;

import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

/** An event listener that runs a list of others */
@JBossLog
public class MultiEventListenerProvider implements EventListenerProvider {

  private final KeycloakSession session;
  private final List<EventListenerProvider> providers;
  private final boolean async;
  private final ExecutorService exec;

  public MultiEventListenerProvider(
      KeycloakSession session,
      List<EventListenerProvider> providers,
      boolean async,
      ExecutorService exec) {
    this.session = session;
    this.providers = providers;
    this.async = async;
    this.exec = exec;
  }

  @Override
  public void onEvent(Event event) {
    providers.forEach(
        p -> {
          run(() -> p.onEvent(event));
        });
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean b) {
    providers.forEach(
        p -> {
          run(() -> p.onEvent(adminEvent, b));
        });
  }

  private void run(Runnable task) {
    try {
      if (async) exec.submit(task);
      else task.run();
    } catch (Exception e) {
      log.warn("Problem running EventListenerProvider", e);
    }
  }

  @Override
  public void close() {
    try {
      if (exec != null) exec.shutdown();
    } catch (Exception e) {
    }
  }
}
