package io.phasetwo.keycloak.events;

import com.github.xgp.util.BackOff;
import io.phasetwo.keycloak.config.Configurable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.utils.ModelToRepresentation;

@JBossLog
public abstract class SenderEventListenerProvider implements EventListenerProvider, Configurable {

  protected final KeycloakSession session;
  protected final ScheduledExecutorService exec;

  public SenderEventListenerProvider(KeycloakSession session, ScheduledExecutorService exec) {
    this.session = session;
    this.exec = exec;
  }

  protected Map<String, Object> config;

  @Override
  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  @Override
  public void onEvent(Event event) {
    schedule(
        new SenderTask(ModelToRepresentation.toRepresentation(event), getBackOff()),
        0l,
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void onEvent(AdminEvent event, boolean b) {
    schedule(
        new SenderTask(ModelToRepresentation.toRepresentation(event), getBackOff()),
        0l,
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    // close this instance of the event listener
  }

  class SenderTask {
    private final Object event;
    private final BackOff backOff;

    public SenderTask(Object event, BackOff backOff) {
      this.event = event;
      this.backOff = backOff;
    }

    public Object getEvent() {
      return this.event;
    }

    public BackOff getBackOff() {
      return this.backOff;
    }
  }

  class SenderException extends Exception {
    private final boolean retryable;

    public SenderException(boolean retryable) {
      super();
      this.retryable = retryable;
    }

    public boolean isRetryable() {
      return this.retryable;
    }
  }

  BackOff getBackOff() {
    return BackOff.STOP_BACKOFF;
  }

  void schedule(SenderTask task, long delay, TimeUnit unit) {
    if (exec.isShutdown()) {
      log.warn("Task scheduled after shutdown initiated");
      return;
    }
    exec.schedule(
        () -> {
          try {
            send(task);
          } catch (SenderException | IOException e) {
            if (e instanceof SenderException && !((SenderException) e).isRetryable()) return;
            long backOffTime = task.getBackOff().nextBackOffMillis();
            if (backOffTime == BackOff.STOP) return;
            schedule(task, backOffTime, TimeUnit.MILLISECONDS);
          } catch (Throwable t) {
            log.warn("Uncaught Sender error", t);
          }
        },
        delay,
        unit);
  }

  abstract void send(SenderTask task) throws SenderException, IOException;
}
