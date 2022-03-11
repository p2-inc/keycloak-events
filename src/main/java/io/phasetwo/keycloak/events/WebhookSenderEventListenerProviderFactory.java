package io.phasetwo.keycloak.events;

import com.google.auto.service.AutoService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;

@JBossLog
@AutoService(EventListenerProviderFactory.class)
public class WebhookSenderEventListenerProviderFactory extends MultiEventListenerProviderFactory {

  public static final String PROVIDER_ID = "ext-event-webhook";

  private ScheduledExecutorService exec;

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  protected EventListenerProvider configure(KeycloakSession session, Map<String, Object> config) {
    WebhookSenderEventListenerProvider provider =
        new WebhookSenderEventListenerProvider(session, exec);
    provider.setConfig(config);
    return provider;
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

  protected boolean isAsync() {
    return true;
  }
}
