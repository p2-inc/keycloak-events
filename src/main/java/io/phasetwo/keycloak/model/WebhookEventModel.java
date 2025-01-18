package io.phasetwo.keycloak.model;

import java.io.IOException;

/** relationship between a Keycloak event (login or admin) and a webhook payload */
public interface WebhookEventModel {

  String getId();

  KeycloakEventType getEventType();

  String getEventId();

  String getAdminEventId();

  String rawPayload();

  <T> T getPayload(Class<T> clazz) throws IOException;
}
