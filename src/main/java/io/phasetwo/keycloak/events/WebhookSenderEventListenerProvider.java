package io.phasetwo.keycloak.events;

import com.google.common.base.Strings;
import io.phasetwo.keycloak.model.KeycloakEventType;
import io.phasetwo.keycloak.model.WebhookEventModel;
import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.model.WebhookSendModel;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.util.JsonSerialization;

@JBossLog
public class WebhookSenderEventListenerProvider extends HttpSenderEventListenerProvider {

  private static final String WEBHOOK_URI_ENV = "WEBHOOK_URI";
  private static final String WEBHOOK_SECRET_ENV = "WEBHOOK_SECRET";
  private static final String WEBHOOK_ALGORITHM_ENV = "WEBHOOK_ALGORITHM";
  private static final String WEBHOOK_AUTH_TYPE_ENV = "WEBHOOK_AUTH_TYPE";
  private static final String WEBHOOK_AUDIENCE_ENV = "WEBHOOK_AUDIENCE";

  public static final String WEBHOOK_SEND_LOGGER_NAME = "io.phasetwo.keycloak.WEBHOOK_SEND_LOGGER";
  public static final String MDC_KEY_PREFIX = "webhook.";
  public static final String LOG_MESSAGE = "Webhook Send";

  private static final Logger WEBHOOK_SEND_LOGGER = Logger.getLogger(WEBHOOK_SEND_LOGGER_NAME);

  private final RunnableTransaction runnableTrx;
  private final KeycloakSessionFactory factory;

  private final WebhookSenderConfig config;
  private final WebhookProvider webhooks;

  private final String systemUri;
  private final String systemSecret;
  private final String systemAlgorithm;
  private final String systemAuthType;
  private final String systemAudience;

  // request base URI captured at construction (request thread has context); used as a fallback for
  // deriving the JWT issuer when signing later on a background thread with no request context.
  private final String contextBaseUri;

  public WebhookSenderEventListenerProvider(
      KeycloakSession session, ScheduledExecutorService exec, WebhookSenderConfig config) {
    super(session, exec);
    this.factory = session.getKeycloakSessionFactory();
    this.runnableTrx = new RunnableTransaction();
    session.getTransactionManager().enlistAfterCompletion(runnableTrx);
    // for system owner catch-all
    this.systemUri = System.getenv(WEBHOOK_URI_ENV);
    this.systemSecret = System.getenv(WEBHOOK_SECRET_ENV);
    this.systemAlgorithm = System.getenv(WEBHOOK_ALGORITHM_ENV);
    this.systemAuthType = System.getenv(WEBHOOK_AUTH_TYPE_ENV);
    this.systemAudience = System.getenv(WEBHOOK_AUDIENCE_ENV);
    this.contextBaseUri = captureBaseUri(session);
    this.config = config;
    this.webhooks = session.getProvider(WebhookProvider.class);
  }

  private static String captureBaseUri(KeycloakSession session) {
    try {
      return session.getContext().getUri().getBaseUri().toString();
    } catch (Exception e) {
      log.tracef("couldn't capture request base URI: %s", e.getMessage());
      return null;
    }
  }

  @Override
  public void onEvent(Event event) {
    log.debugf("onEvent %s %s", event.getType(), event.getId());
    try {
      ExtendedAdminEvent customEvent = completeAdminEventAttributes("", event);
      runnableTrx.addRunnable(
          () -> processEvent(KeycloakEventType.USER, customEvent, event.getRealmId()));
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
      runnableTrx.addRunnable(
          () -> processEvent(KeycloakEventType.ADMIN, customEvent, adminEvent.getRealmId()));
    } catch (Exception e) {
      log.warn("Error converting and scheduling event: " + adminEvent, e);
    }
  }

  private synchronized void storeEvent(
      KeycloakSession session, KeycloakEventType type, ExtendedAdminEvent event) {
    if (!config.shouldStoreWebhookEvents()) {
      log.tracef("storeWebhookEvents is %s. skipping...", config.shouldStoreWebhookEvents());
      return;
    }
    if (!type.keycloakNative()) {
      log.tracef("%S event. Skipping event storage.", type);
      return;
    }

    RealmModel realm = session.realms().getRealm(event.getRealmId());
    Set<String> eventTypes = realm.getEnabledEventTypesStream().collect(Collectors.toSet());
    EventType eventType = event.getNativeType();
    if (type == KeycloakEventType.USER && !realm.isEventsEnabled()) {
      log.tracef("USER events disabled for realm %s", realm.getName());
      return;
    }
    if (type == KeycloakEventType.USER
        && !(eventTypes.isEmpty() && eventType.isSaveByDefault()
            || eventTypes.contains(eventType.name()))) {
      log.tracef(
          "USER events not persisted for event type %s for realm %s ", eventType, realm.getName());
      return;
    }

    if (type == KeycloakEventType.ADMIN && !realm.isAdminEventsEnabled()) {
      log.tracef("ADMIN events disabled for realm %s", realm.getName());
      return;
    }

    // look it up first, as we might have multiple webhooks
    WebhookEventModel we = webhooks.getEvent(realm, type, event.getId());
    if (we != null) {
      log.tracef("Webhook event %s already stored. Skipping.", event.getId());
      return;
    }

    we = webhooks.storeEvent(realm, type, event.getId(), event);
    log.tracef(
        "Webhook event stored [%s] %s, %s, %s, %s",
        we.getId(), event.getRealmId(), we.getEventType(), we.getEventId(), we.getAdminEventId());
  }

  public void processEvent(ExtendedAdminEvent event, String realmId) {
    processEvent(KeycloakEventType.fromTypeString(event.getType()), event, realmId);
  }

  /** Schedule dispatch to all webhooks and system */
  private void processEvent(KeycloakEventType type, ExtendedAdminEvent event, String realmId) {
    KeycloakModelUtils.runJobInTransaction(
        factory,
        (session) -> {
          if (type.keycloakNative()) {
            storeEvent(session, type, event);
          }
          RealmModel realm = session.realms().getRealm(realmId);
          WebhookProvider webhooks = session.getProvider(WebhookProvider.class);
          webhooks
              .getWebhooksStream(realm)
              .filter(w -> w.isEnabled())
              .filter(w -> !Strings.isNullOrEmpty(w.getUrl()))
              .forEach(
                  w -> {
                    ExtendedAdminEvent customEvent = clone(event);
                    customEvent.setUid(KeycloakModelUtils.generateId());
                    log.tracef("Got custom event with UID %s", customEvent.getUid());
                    if (!enabledFor(w, customEvent)) return;
                    schedule(w, customEvent);
                  });
          // for system owner catch-all
          if (!Strings.isNullOrEmpty(systemUri)) {
            ExtendedAdminEvent customEvent = clone(event);
            customEvent.setUid(KeycloakModelUtils.generateId());
            schedule(
                null,
                customEvent,
                systemUri,
                systemSecret,
                systemAlgorithm,
                systemAuthType,
                systemAudience);
          }
        });
  }

  @Override
  protected synchronized void afterSend(final SenderTask task, final int httpStatus) {
    final String webhookId = task.getProperties().get("webhookId");
    if (webhookId == null) return;
    final ExtendedAdminEvent customEvent = (ExtendedAdminEvent) task.getEvent();
    final KeycloakEventType eventType = KeycloakEventType.fromTypeString(customEvent.getType());
    if (!eventType.keycloakNative()) {
      log.tracef("%s event type. Skipping send storage.", customEvent.getType());
      return;
    }

    final Date sentAt = new Date();

    if (config.shouldStoreWebhookEvents()) {
      storeWebhookSend(task, customEvent, webhookId, httpStatus, sentAt);
    }
    if (config.shouldLogWebhookEvents()) {
      logWebhookSend(task, customEvent, eventType, webhookId, httpStatus, sentAt);
    }
  }

  private void storeWebhookSend(
      final SenderTask task,
      final ExtendedAdminEvent customEvent,
      final String webhookId,
      final int httpStatus,
      final Date sentAt) {
    KeycloakModelUtils.runJobInTransaction(
        factory,
        (session) -> {
          RealmModel realm = session.realms().getRealm(customEvent.getRealmId());
          WebhookProvider webhooks = session.getProvider(WebhookProvider.class);
          WebhookModel webhook = webhooks.getWebhookById(realm, webhookId);
          WebhookEventModel event =
              webhooks.getEvent(
                  realm,
                  KeycloakEventType.fromTypeString(customEvent.getType()),
                  customEvent.getId());
          if (event == null) {
            log.tracef(
                "No event for [%s] %s. Skipping send storage.",
                customEvent.getType(), customEvent.getId());
          } else {
            // look it up first, as we might be here for a retry/resend
            WebhookSendModel webhookSend = webhooks.getSendById(realm, customEvent.getUid());
            if (webhookSend == null) {
              webhookSend =
                  webhooks.storeSend(webhook, event, customEvent.getUid(), customEvent.getType());
            }
            webhookSend.setStatus(httpStatus);
            webhookSend.incrementRetries();
            webhookSend.setSentAt(sentAt);
          }
        });
  }

  private void logWebhookSend(
      final SenderTask task,
      final ExtendedAdminEvent customEvent,
      final KeycloakEventType eventType,
      final String webhookId,
      final int httpStatus,
      final Date sentAt) {
    String rawPayload = null;
    try {
      rawPayload = JsonSerialization.writeValueAsString(customEvent);
    } catch (IOException e) {
      log.warnf(e, "Unable to serialize webhook payload for logging [%s]", customEvent.getUid());
    }
    FlatWebhook flat =
        new FlatWebhook(
            eventType.name(),
            customEvent.getId(),
            webhookId,
            customEvent.getUid(),
            httpStatus,
            task.getAttempt(),
            sentAt.getTime(),
            rawPayload);
    try (LogContext ctx = LogContext.with(flat, MDC_KEY_PREFIX)) {
      WEBHOOK_SEND_LOGGER.info(LOG_MESSAGE);
    }
  }

  public void schedule(WebhookModel webhook, ExtendedAdminEvent customEvent) {
    schedule(
        webhook.getId(),
        customEvent,
        webhook.getUrl(),
        webhook.getSecret(),
        webhook.getAlgorithm(),
        webhook.getAuthType(),
        webhook.getAudience());
  }

  private void schedule(
      String webhookId,
      ExtendedAdminEvent customEvent,
      String url,
      String secret,
      String algorithm,
      String authType,
      String audience) {
    SenderTask task = new SenderTask(customEvent, getBackOff());
    task.getProperties().put("webhookId", webhookId);
    task.getProperties().put("url", url);
    if (secret != null) task.getProperties().put("secret", secret);
    if (algorithm != null) task.getProperties().put("algorithm", algorithm);
    if (authType != null) task.getProperties().put("authType", authType);
    if (audience != null) task.getProperties().put("audience", audience);
    if (customEvent.getRealmId() != null) {
      task.getProperties().put("realmId", customEvent.getRealmId());
    }
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
    String targetUri = task.getProperties().get("url");
    String authType = task.getProperties().get("authType");
    if (WebhookModel.AUTH_TYPE_BEARER.equalsIgnoreCase(authType)) {
      String bearer = generateBearerToken(task);
      send(
          task,
          targetUri,
          request -> {
            if (bearer != null) request.header("Authorization", "Bearer " + bearer);
          });
    } else {
      Optional<String> sharedSecret = Optional.ofNullable(task.getProperties().get("secret"));
      Optional<String> hmacAlgorithm = Optional.ofNullable(task.getProperties().get("algorithm"));
      send(task, targetUri, sharedSecret, hmacAlgorithm);
    }
  }

  /**
   * Mint a fresh realm-signed JWT for this send attempt. Runs in its own transaction because {@code
   * send} executes on a background scheduler thread with no live request session, and signing needs
   * the realm's active signing key.
   */
  private String generateBearerToken(SenderTask task) {
    String realmId = task.getProperties().get("realmId");
    if (Strings.isNullOrEmpty(realmId)) {
      log.warn("Cannot generate webhook bearer token: missing realmId");
      return null;
    }
    String algorithm = task.getProperties().get("algorithm");
    String audience = task.getProperties().get("audience");
    final AtomicReference<String> token = new AtomicReference<>();
    try {
      final String body = JsonSerialization.writeValueAsString(task.getEvent());
      KeycloakModelUtils.runJobInTransaction(
          factory,
          (session) -> {
            RealmModel realm = session.realms().getRealm(realmId);
            if (realm == null) {
              log.warnf("Cannot generate webhook bearer token: realm %s not found", realmId);
              return;
            }
            token.set(
                WebhookJwtSigner.sign(session, realm, algorithm, audience, body, contextBaseUri));
          });
    } catch (Exception e) {
      log.warn("Error generating webhook bearer token", e);
    }
    return token.get();
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
    RealmModel eventRealm = session.realms().getRealm(adminEvent.getRealmId());
    RealmModel authRealm = session.realms().getRealm(adminEvent.getAuthDetails().getRealmId());
    ExtendedAdminEvent extendedAdminEvent =
        new ExtendedAdminEvent(uid, adminEvent, eventRealm, authRealm);
    // add always missing agent username
    ExtendedAuthDetails extendedAuthDetails = extendedAdminEvent.getAuthDetails();
    if (!Strings.isNullOrEmpty(extendedAuthDetails.getUserId())) {
      UserModel user = session.users().getUserById(authRealm, extendedAuthDetails.getUserId());
      if (user != null) {
        extendedAuthDetails.setUsername(user.getUsername());
      }
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
      log.tracef("couldn't get sessionId: %s", e.getMessage());
    }
    try {
      details.setRealmId(
          session.getContext().getAuthenticationSession().getParentSession().getRealm().getName());
    } catch (Exception e) {
      log.tracef("couldn't get realmId: %s", e.getMessage());
    }
    return event;
  }

  /** deep clone the event */
  private static ExtendedAdminEvent clone(ExtendedAdminEvent event) {
    try {
      ExtendedAdminEvent customEvent =
          JsonSerialization.readValue(
              JsonSerialization.writeValueAsString(event), ExtendedAdminEvent.class);
      customEvent.setUid(null);
      return customEvent;
    } catch (IOException e) {
      throw new IllegalStateException("Event can't be cloned because of serialization issue.", e);
    }
  }
}
