package io.phasetwo.keycloak.events;

import java.util.LinkedList;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.AbstractKeycloakTransaction;

@JBossLog
public class RunnableTransaction extends AbstractKeycloakTransaction {

  protected final List<Runnable> runnables;

  public RunnableTransaction() {
    this.runnables = new LinkedList<Runnable>();
  }

  public void addRunnable(Runnable r) {
    runnables.add(r);
  }

  @Override
  protected void commitImpl() {
    try {
      runnables.forEach(
          task -> {
            try {
              task.run();
            } catch (Exception e1) {
              log.warn("Error running Runnable", e1);
            }
          });
    } catch (Exception e2) {
      log.warn("Error running RunnableTransaction", e2);
    }
  }

  @Override
  protected void rollbackImpl() {
    runnables.clear();
  }
}
