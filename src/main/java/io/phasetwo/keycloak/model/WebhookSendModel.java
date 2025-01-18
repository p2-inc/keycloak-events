package io.phasetwo.keycloak.model;

import java.util.Date;

/** records the sending of a webhook event to a webhook endpoint */
public interface WebhookSendModel {

  String getId();

  WebhookModel getWebhook();

  WebhookEventModel getEvent();

  Integer getFinalStatus();

  Integer getRetries();

  Date getSentAt();
}
