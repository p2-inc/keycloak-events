package io.phasetwo.keycloak.events;

import java.util.Map;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class Events {

  public static RealmModel getRealm(KeycloakSession session, Event event) {
    return session.realms().getRealm(event.getRealmId());
  }

  public static RealmModel getRealm(KeycloakSession session, AdminEvent event) {
    return session.realms().getRealm(event.getRealmId());
  }

  public static UserModel getUser(KeycloakSession session, Event event) {
    RealmModel realm = getRealm(session, event);
    return (event.getUserId() != null)
        ? session.users().getUserById(realm, event.getUserId())
        : null;
  }

  public static UserModel getAuthUser(KeycloakSession session, AdminEvent event) {
    RealmModel realm = getRealm(session, event);
    return (event.getAuthDetails().getUserId() != null)
        ? session.users().getUserById(realm, event.getAuthDetails().getUserId())
        : null;
  }

  public static String toString(Event event) {
    StringBuilder sb = new StringBuilder();
    sb.append("type=");
    sb.append(event.getType());
    sb.append(", time=");
    sb.append(event.getTime());
    sb.append(", realmId=");
    sb.append(event.getRealmId());
    sb.append(", clientId=");
    sb.append(event.getClientId());
    sb.append(", sessionId=");
    sb.append(event.getSessionId());
    sb.append(", userId=");
    sb.append(event.getUserId());
    sb.append(", ipAddress=");
    sb.append(event.getIpAddress());
    if (event.getError() != null) {
      sb.append(", error=");
      sb.append(event.getError());
    }
    if (event.getDetails() != null) {
      for (Map.Entry<String, String> e : event.getDetails().entrySet()) {
        sb.append(", ");
        sb.append(e.getKey());
        if (e.getValue() == null || e.getValue().indexOf(' ') == -1) {
          sb.append("=");
          sb.append(e.getValue());
        } else {
          sb.append("='");
          sb.append(e.getValue());
          sb.append("'");
        }
      }
    }
    return sb.toString();
  }

  public static String toString(AdminEvent adminEvent) {
    StringBuilder sb = new StringBuilder();
    sb.append("operationType=");
    sb.append(adminEvent.getOperationType());
    sb.append(", realmId=");
    sb.append(adminEvent.getAuthDetails().getRealmId());
    sb.append(", clientId=");
    sb.append(adminEvent.getAuthDetails().getClientId());
    sb.append(", userId=");
    sb.append(adminEvent.getAuthDetails().getUserId());
    sb.append(", ipAddress=");
    sb.append(adminEvent.getAuthDetails().getIpAddress());
    sb.append(", resourcePath=");
    sb.append(adminEvent.getResourcePath());
    if (adminEvent.getError() != null) {
      sb.append(", error=");
      sb.append(adminEvent.getError());
    }
    return sb.toString();
  }
}
