package io.phasetwo.keycloak.model;

import java.io.IOException;
import org.keycloak.models.RealmModel;

/** relationship between a Keycloak event (login or admin) and a webhook payload */
public interface WebhookEventModel {

  String getId();

  RealmModel getRealm();

  KeycloakEventType getEventType();

  String getEventId();

  String getAdminEventId();

  String rawPayload();

  <T> T getPayload(Class<T> clazz) throws IOException;
}
