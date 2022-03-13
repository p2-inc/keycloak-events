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
  private RunnableTransaction runnableTrx;

  public MultiEventListenerProvider(
      KeycloakSession session,
      List<EventListenerProvider> providers,
      boolean async,
      ExecutorService exec) {
    this.session = session;
    this.providers = providers;
    this.async = async;
    this.exec = exec;
    if (async) {
      runnableTrx = new RunnableTransaction();
      session.getTransactionManager().enlistAfterCompletion(runnableTrx);
    }
  }

  @Override
  public void onEvent(Event event) {
    log.debugf("onEvent %s %s", event.getType(), event.getId());
    providers.forEach(
        p -> {
          run(() -> p.onEvent(event));
        });
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean b) {
    log.debugf(
        "onEvent %s %s %s",
        adminEvent.getOperationType(), adminEvent.getResourceType(), adminEvent.getResourcePath());
    providers.forEach(
        p -> {
          run(() -> p.onEvent(adminEvent, b));
        });
  }

  private void run(Runnable task) {
    try {
      if (async) runnableTrx.addRunnable(task);
      else task.run();
    } catch (Exception e) {
      log.warn("Problem running EventListenerProvider", e);
    }
  }

  @Override
  public void close() {}
}
