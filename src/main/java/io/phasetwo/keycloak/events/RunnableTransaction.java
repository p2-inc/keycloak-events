package io.phasetwo.keycloak.events;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.AbstractKeycloakTransaction;

@JBossLog
public class RunnableTransaction extends AbstractKeycloakTransaction {

  private final ExecutorService exec;
  private final List<Runnable> runnables;

  public RunnableTransaction(ExecutorService exec) {
    this.exec = exec;
    this.runnables = new LinkedList<Runnable>();
  }

  public void addRunnable(Runnable r) {
    runnables.add(r);
  }

  @Override
  protected void commitImpl() {
    try {
      runnables.forEach(task -> exec.submit(task));
    } catch (Exception e) {
      log.warn("Problem running RunnableTransaction", e);
    }
  }

  @Override
  protected void rollbackImpl() {
    runnables.clear();
  }
}
