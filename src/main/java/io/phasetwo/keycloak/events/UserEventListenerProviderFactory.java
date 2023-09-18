package io.phasetwo.keycloak.events;

import io.phasetwo.keycloak.ext.event.AbstractEventListenerProvider;
import io.phasetwo.keycloak.ext.event.AbstractEventListenerProviderFactory;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * User added/removed listener base class. Just provide a user add/remove handler. Inspired by
 * zonaut's work:
 * https://keycloak.discourse.group/t/created-user-not-immediately-available-on-event/1476
 */
@JBossLog
public abstract class UserEventListenerProviderFactory
    extends AbstractEventListenerProviderFactory {

  private KeycloakSessionFactory factory;

  @Override
  public EventListenerProvider create(KeycloakSession session) {
    return new AbstractEventListenerProvider() {
      @Override
      public void onEvent(Event event) {
        if (EventType.REGISTER.equals(event.getType())) {
          userAdded(event.getRealmId(), event.getUserId());
        }
      }

      @Override
      public void onEvent(AdminEvent adminEvent, boolean b) {
        if (ResourceType.USER.equals(adminEvent.getResourceType())
            && OperationType.CREATE.equals(adminEvent.getOperationType())) {
          String resourcePath = adminEvent.getResourcePath();
          if (resourcePath.startsWith("users/")) {
            userAdded(adminEvent.getRealmId(), resourcePath.substring("users/".length()));
          } else {
            log.warnf(
                "AdminEvent was CREATE:USER without appropriate resourcePath=%s", resourcePath);
          }
        }
      }

      void userAdded(String realmId, String userId) {
        session
            .getTransactionManager()
            .enlistAfterCompletion(
                new AbstractKeycloakTransaction() {
                  @Override
                  protected void commitImpl() {
                    KeycloakModelUtils.runJobInTransaction(
                        factory,
                        (s) -> {
                          RealmModel realm = s.realms().getRealm(realmId);
                          UserModel user = s.users().getUserById(realm, userId);
                          getUserChangedHandler().onUserAdded(s, realm, user);
                        });
                  }

                  @Override
                  protected void rollbackImpl() {}
                });
      }
    };
  }

  abstract UserChangedHandler getUserChangedHandler();

  abstract class UserChangedHandler {
    abstract void onUserAdded(KeycloakSession session, RealmModel realm, UserModel user);

    abstract void onUserRemoved(KeycloakSession session, RealmModel realm, UserModel user);
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    this.factory = factory;
    factory.register(
        (event) -> {
          if (event instanceof UserModel.UserRemovedEvent) {
            UserModel.UserRemovedEvent removal = (UserModel.UserRemovedEvent) event;
            getUserChangedHandler()
                .onUserRemoved(removal.getKeycloakSession(), removal.getRealm(), removal.getUser());
          }
        });
  }
}
