package io.phasetwo.keycloak.events;

import com.google.common.base.Strings;
import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

@JBossLog
public class WebhookSenderEventListenerProvider extends HttpSenderEventListenerProvider {

  private static final String WEBHOOK_URI_ENV = "WEBHOOK_URI";
  private static final String WEBHOOK_SECRET_ENV = "WEBHOOK_SECRET";
  private static final String WEBHOOK_ALGORITHM_ENV = "WEBHOOK_ALGORITHM";

  private final RunnableTransaction runnableTrx;
  private final KeycloakSessionFactory factory;

  private final String systemUri;
  private final String systemSecret;
  private final String systemAlgorithm;

  public WebhookSenderEventListenerProvider(
      KeycloakSession session, ScheduledExecutorService exec) {
    super(session, exec);
    this.factory = session.getKeycloakSessionFactory();
    this.runnableTrx = new RunnableTransaction();
    session.getTransactionManager().enlistAfterCompletion(runnableTrx);
    // for system owner catch-all
    this.systemUri = System.getenv(WEBHOOK_URI_ENV);
    this.systemSecret = System.getenv(WEBHOOK_SECRET_ENV);
    this.systemAlgorithm = System.getenv(WEBHOOK_ALGORITHM_ENV);
  }

  @Override
  public void onEvent(Event event) {
    log.debugf("onEvent %s %s", event.getType(), event.getId());
    try {
      ExtendedAdminEvent customEvent = completeAdminEventAttributes("", event);
      runnableTrx.addRunnable(() -> processEvent(customEvent, event.getRealmId()));
    } catch (Exception e) {
      log.warn("Error converting and scheduling event: " + event, e);
    }
  }

  @Override
  public void onEvent(AdminEvent adminEvent, boolean b) {
    log.debugf(
        "onEvent %s %s %s",
        adminEvent.getOperationType(),
        adminEvent.getResourceTypeAsString(),
        adminEvent.getResourcePath());
    try {
      ExtendedAdminEvent customEvent = completeAdminEventAttributes("", adminEvent);
      runnableTrx.addRunnable(() -> processEvent(customEvent, adminEvent.getRealmId()));
    } catch (Exception e) {
      log.warn("Error converting and scheduling event: " + adminEvent, e);
    }
  }

  /** Update the event with a unique uid */
  public void processEvent(ExtendedAdminEvent customEvent, String realmId) {
    processEvent(
        () -> {
          customEvent.setUid(KeycloakModelUtils.generateId());
          return customEvent;
        },
        realmId);
  }

  /** Schedule dispatch to all webhooks and system */
  private void processEvent(Supplier<ExtendedAdminEvent> supplier, String realmId) {
    KeycloakModelUtils.runJobInTransaction(
        factory,
        (session) -> {
          RealmModel realm = session.realms().getRealm(realmId);
          WebhookProvider webhooks = session.getProvider(WebhookProvider.class);
          webhooks
              .getWebhooksStream(realm)
              .filter(w -> w.isEnabled())
              .filter(w -> !Strings.isNullOrEmpty(w.getUrl()))
              .forEach(
                  w -> {
                    ExtendedAdminEvent customEvent = supplier.get();
                    if (!enabledFor(w, customEvent)) return;
                    schedule(customEvent, w.getUrl(), w.getSecret(), w.getAlgorithm());
                  });
          // for system owner catch-all
          if (!Strings.isNullOrEmpty(systemUri)) {
            schedule(supplier.get(), systemUri, systemSecret, systemAlgorithm);
          }
        });
  }

  private void schedule(
      ExtendedAdminEvent customEvent, String url, String secret, String algorithm) {
    SenderTask task = new SenderTask(customEvent, getBackOff());
    task.getProperties().put("url", url);
    task.getProperties().put("secret", secret);
    task.getProperties().put("algorithm", algorithm);
    schedule(task, 0l, TimeUnit.MILLISECONDS);
  }

  /** Check if the event type is enabled for this webhook */
  private boolean enabledFor(WebhookModel webhook, ExtendedAdminEvent customEvent) {
    String type = customEvent.getType();
    log.debugf("Checking webhook enabled for %s [%s]", type, webhook.getEventTypes());
    for (String t : webhook.getEventTypes()) {
      if ("*".equals(t)) return true;
      if ("access.*".equals(t) && type.startsWith("access.")) return true;
      if ("admin.*".equals(t) && type.startsWith("admin.")) return true;
      if ("system.*".equals(t) && type.startsWith("system.")) return true;
      try {
        if (Pattern.matches(t, type)) return true;
      } catch (Exception e) {
      }
      if (t.equals(type)) return true;
    }
    return false;
  }

  @Override
  void send(SenderTask task) throws SenderException, IOException {
    String sharedSecret = task.getProperties().get("secret");
    String targetUri = task.getProperties().get("url");
    Optional<String> hmacAlgorithm = Optional.ofNullable(task.getProperties().get("algorithm"));
    send(task, targetUri, sharedSecret, hmacAlgorithm);
  }

  private ExtendedAdminEvent completeAdminEventAttributes(String uid, Event event) {
    RealmModel realm = session.realms().getRealm(event.getRealmId());
    ExtendedAdminEvent extendedAdminEvent = new ExtendedAdminEvent(uid, event, realm);
    if (!Strings.isNullOrEmpty(event.getUserId())) {
      // retrieve username from userId
      UserModel user = session.users().getUserById(realm, event.getUserId());
      if (user != null) {
        extendedAdminEvent.getAuthDetails().setUsername(user.getUsername());
      }
    }
    completeExtendedAuthDetails(extendedAdminEvent);
    return extendedAdminEvent;
  }

  private ExtendedAdminEvent completeAdminEventAttributes(String uid, AdminEvent adminEvent) {
    RealmModel realm = session.realms().getRealm(adminEvent.getRealmId());
    ExtendedAdminEvent extendedAdminEvent = new ExtendedAdminEvent(uid, adminEvent, realm);
    // add always missing agent username
    ExtendedAuthDetails extendedAuthDetails = extendedAdminEvent.getAuthDetails();
    if (!Strings.isNullOrEmpty(extendedAuthDetails.getUserId())) {
      UserModel user = session.users().getUserById(realm, extendedAuthDetails.getUserId());
      extendedAuthDetails.setUsername(user.getUsername());
    }
    // add username if resource is a user
    String resourcePath = extendedAdminEvent.getResourcePath();
    if (resourcePath != null && resourcePath.startsWith("users")) {
      // parse userId
      String pattern =
          "^users/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$";
      Pattern r = Pattern.compile(pattern);
      Matcher m = r.matcher(resourcePath);
      if (m.matches()) {
        String userId = m.group(1);
        // retrieve user
        UserModel user =
            session.users().getUserById(session.realms().getRealm(adminEvent.getRealmId()), userId);
        extendedAdminEvent.getDetails().put("userId", userId);
        if (user != null) {
          extendedAdminEvent.getDetails().put("username", user.getUsername());
        }
      }
    }
    completeExtendedAuthDetails(extendedAdminEvent);
    return extendedAdminEvent;
  }

  private ExtendedAdminEvent completeExtendedAuthDetails(ExtendedAdminEvent event) {
    ExtendedAuthDetails details = event.getAuthDetails();
    if (details == null) return event;
    try {
      details.setSessionId(
          session.getContext().getAuthenticationSession().getParentSession().getId());
    } catch (Exception e) {
      log.debug("couldn't get sessionId", e);
    }
    try {
      details.setRealmId(
          session.getContext().getAuthenticationSession().getParentSession().getRealm().getName());
    } catch (Exception e) {
      log.debug("couldn't get realmId", e);
    }
    return event;
  }
}
