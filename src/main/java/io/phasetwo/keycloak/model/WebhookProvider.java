package io.phasetwo.keycloak.model;

import java.util.stream.Stream;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;

public interface WebhookProvider extends Provider {

  /** store a webhook event * */
  WebhookEventModel storeEvent(
      RealmModel realm, KeycloakEventType type, String id, Object eventObject);

  /** given a Keycloak event, show the webhook payload */
  WebhookEventModel getEvent(RealmModel realm, KeycloakEventType type, String id);

  /** store a webhook send * */
  WebhookSendModel storeSend(WebhookModel webhook, WebhookEventModel event, String id, String type);

  /** given a Keycloak event, show all webhook sends that were triggered */
  default Stream<WebhookSendModel> getSends(RealmModel realm, KeycloakEventType type, String id) {
    return getSends(realm, getEvent(realm, type, id));
  }

  /** given a webhook event, show all webhook sends that were triggered */
  Stream<WebhookSendModel> getSends(RealmModel realm, WebhookEventModel event);

  /** given a webhook, show the sends to it, sorted by date (inv) */
  Stream<WebhookSendModel> getSends(
      RealmModel realm, WebhookModel webhook, Integer firstResult, Integer maxResults);

  WebhookSendModel getSendById(RealmModel realm, String id);

  /** create a new webhook with a url and a user who created it */
  WebhookModel createWebhook(RealmModel realm, String url, UserModel createdBy);

  /** get a webhook by its ID */
  WebhookModel getWebhookById(RealmModel realm, String id);

  /** get all webhooks, paginated */
  Stream<WebhookModel> getWebhooksStream(RealmModel realm, Integer firstResult, Integer maxResults);

  /** get all webhooks */
  default Stream<WebhookModel> getWebhooksStream(RealmModel realm) {
    return getWebhooksStream(realm, null, null);
  }

  /** count all webhooks */
  Long getWebhooksCount(RealmModel realm);

  /** remove a webhook by its ID */
  boolean removeWebhook(RealmModel realm, String id);

  /** remove all webhooks for a given realm */
  void removeWebhooks(RealmModel realm);
}
