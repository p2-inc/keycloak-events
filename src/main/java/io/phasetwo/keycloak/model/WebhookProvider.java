package io.phasetwo.keycloak.model;

import java.util.stream.Stream;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

public interface WebhookProvider extends Provider {

  WebhookModel createWebhook(RealmModel realm, String url, UserModel createdBy);

  WebhookModel getWebhookById(RealmModel realm, String id);

  Stream<WebhookModel> getWebhooksStream(RealmModel realm, Integer firstResult, Integer maxResults);

  default Stream<WebhookModel> getWebhooksStream(RealmModel realm) {
    return getWebhooksStream(realm, null, null);
  }

  Long getWebhooksCount(RealmModel realm);

  boolean removeWebhook(RealmModel realm, String id);

  void removeWebhooks(RealmModel realm);
}
