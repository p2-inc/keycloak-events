package io.phasetwo.keycloak.events;

import com.google.auto.service.AutoService;
import com.google.common.util.concurrent.MoreExecutors;
import io.phasetwo.keycloak.events.auth.OAuth2TokenCache;
import java.time.Duration;
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
  private WebhookSenderConfig config = new WebhookSenderConfig(false, false);

  /** Timeout for each phase (connect, pool, read) of an OAuth2 token request. */
  static final Duration OAUTH2_TOKEN_TIMEOUT = Duration.ofSeconds(10);

  // tokens for oauth2-client-credentials webhooks, shared by every session on this node
  private final OAuth2TokenCache oauth2Tokens = new OAuth2TokenCache(OAUTH2_TOKEN_TIMEOUT);

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public WebhookSenderEventListenerProvider create(KeycloakSession session) {
    return new WebhookSenderEventListenerProvider(session, exec, config, oauth2Tokens);
  }

  @Override
  public void init(Config.Scope scope) {
    boolean storeWebhookEvents = scope.getBoolean("storeWebhookEvents", false);
    boolean logWebhookEvents = scope.getBoolean("logWebhookEvents", false);
    config = new WebhookSenderConfig(storeWebhookEvents, logWebhookEvents);
    log.infof("storeWebhookEvents %b, logWebhookEvents %b", storeWebhookEvents, logWebhookEvents);

    exec =
        MoreExecutors.getExitingScheduledExecutorService(
            new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors()));
  }

  @Override
  public void close() {
    try {
      log.debug("Shutting down scheduler");
      exec.shutdown();
      oauth2Tokens.clear();
    } catch (Exception e) {
      log.warn("Error in shutdown of scheduler", e);
    }
  }
}
