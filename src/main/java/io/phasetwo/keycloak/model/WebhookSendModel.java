package io.phasetwo.keycloak.model;

import java.util.Date;

/** records the sending of a webhook event to a webhook endpoint */
public interface WebhookSendModel {

  String getId();

  String getEventType();

  WebhookModel getWebhook();

  WebhookEventModel getEvent();

  Integer getStatus();

  void setStatus(Integer status);

  Integer getRetries();

  void incrementRetries();

  Date getSentAt();

  void setSentAt(Date sentAt);
}
