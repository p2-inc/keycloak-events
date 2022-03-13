package io.phasetwo.keycloak.model;

import java.util.Date;
import java.util.Set;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public interface WebhookModel {

  String getId();

  boolean isEnabled();

  void setEnabled(boolean enabled);

  String getUrl();

  void setUrl(String url);

  String getSecret();

  void setSecret(String secret);

  RealmModel getRealm();

  UserModel getCreatedBy();

  Date getCreatedAt();

  Set<String> getEventTypes();

  void addEventType(String eventType);

  void removeEventTypes();
}
