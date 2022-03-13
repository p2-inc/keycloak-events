package io.phasetwo.keycloak.events;

import com.google.auto.service.AutoService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;

@JBossLog
@AutoService(EventListenerProviderFactory.class)
public class WebhookSenderEventListenerProviderFactory
    extends AbstractEventListenerProviderFactory {

  public static final String PROVIDER_ID = "ext-event-webhook";

  private ScheduledExecutorService exec;

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public WebhookSenderEventListenerProvider create(KeycloakSession session) {
    return new WebhookSenderEventListenerProvider(session, exec);
  }

  @Override
  public void init(Config.Scope scope) {
    exec =
        MoreExecutors.getExitingScheduledExecutorService(
            new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors()));
  }

  @Override
  public void close() {
    try {
      log.info("Shutting down scheduler");
      exec.shutdown();
    } catch (Exception e) {
      log.warn("Error in shutdown of scheduler", e);
    }
  }
}
