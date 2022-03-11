package io.phasetwo.keycloak.events;

import com.google.common.base.Strings;
import io.phasetwo.keycloak.model.WebhookProvider;
import io.phasetwo.keycloak.representation.ExtendedAdminEvent;
import io.phasetwo.keycloak.representation.ExtendedAuthDetails;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

@JBossLog
public class WebhookSenderEventListenerProvider extends HttpSenderEventListenerProvider {

  private final RealmModel realm;
  private final WebhookProvider webhooks;

  public WebhookSenderEventListenerProvider(
      KeycloakSession session, ScheduledExecutorService exec) {
    super(session, exec);
    this.realm = session.getContext().getRealm();
    this.webhooks = session.getProvider(WebhookProvider.class);
  }

  @Override
  public void onEvent(Event event) {
    webhooks
        .getWebhooksStream(realm)
        .filter(w -> w.isEnabled())
        .forEach(
            w -> {
              ExtendedAdminEvent customEvent =
                  completeAdminEventAttributes(KeycloakModelUtils.generateId(), event);
              SenderTask task = new SenderTask(customEvent, getBackOff());
              task.getProperties().put("url", w.getUrl());
              task.getProperties().put("secret", w.getSecret());
              schedule(task, 0l, TimeUnit.MILLISECONDS);
            });
  }

  @Override
  public void onEvent(AdminEvent event, boolean b) {
    webhooks
        .getWebhooksStream(realm)
        .filter(w -> w.isEnabled())
        .forEach(
            w -> {
              ExtendedAdminEvent customEvent =
                  completeAdminEventAttributes(KeycloakModelUtils.generateId(), event);
              SenderTask task = new SenderTask(customEvent, getBackOff());
              task.getProperties().put("url", w.getUrl());
              task.getProperties().put("secret", w.getSecret());
              schedule(task, 0l, TimeUnit.MILLISECONDS);
            });
  }

  @Override
  void send(SenderTask task) throws SenderException, IOException {
    String sharedSecret = task.getProperties().get("secret");
    String targetUri = task.getProperties().get("url");
    send(task, targetUri, sharedSecret);
  }

  private ExtendedAdminEvent completeAdminEventAttributes(String uid, Event event) {
    ExtendedAdminEvent extendedAdminEvent = new ExtendedAdminEvent(uid, event);
    if (!Strings.isNullOrEmpty(event.getUserId())) {
      // retrieve username from userId
      UserModel user =
          session
              .users()
              .getUserById(session.realms().getRealm(event.getRealmId()), event.getUserId());
      if (user != null) {
        extendedAdminEvent.getAuthDetails().setUsername(user.getUsername());
      }
    }
    completeExtendedAuthDetails(extendedAdminEvent);
    return extendedAdminEvent;
  }

  private ExtendedAdminEvent completeAdminEventAttributes(String uid, AdminEvent adminEvent) {
    ExtendedAdminEvent extendedAdminEvent = new ExtendedAdminEvent(uid, adminEvent);
    // add always missing agent username
    ExtendedAuthDetails extendedAuthDetails = extendedAdminEvent.getAuthDetails();
    if (!Strings.isNullOrEmpty(extendedAuthDetails.getUserId())) {
      UserModel user =
          session
              .users()
              .getUserById(
                  session.realms().getRealm(extendedAuthDetails.getRealmId()),
                  extendedAuthDetails.getUserId());
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
