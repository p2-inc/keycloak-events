package io.phasetwo.keycloak.events;

public final class WebhookSenderConfig {

  private final boolean storeWebhookEvents;
  private final boolean logWebhookEvents;

  public WebhookSenderConfig(boolean storeWebhookEvents, boolean logWebhookEvents) {
    this.storeWebhookEvents = storeWebhookEvents;
    this.logWebhookEvents = logWebhookEvents;
  }

  public boolean shouldStoreWebhookEvents() {
    return storeWebhookEvents;
  }

  public boolean shouldLogWebhookEvents() {
    return logWebhookEvents;
  }
}
