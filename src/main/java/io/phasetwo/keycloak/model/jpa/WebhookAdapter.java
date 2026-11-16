package io.phasetwo.keycloak.model.jpa;

import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.jpa.entity.WebhookConfigEntity;
import io.phasetwo.keycloak.model.jpa.entity.WebhookEntity;
import jakarta.persistence.EntityManager;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.utils.KeycloakModelUtils;

public class WebhookAdapter implements WebhookModel, JpaModel<WebhookEntity> {

  protected final KeycloakSession session;
  protected final WebhookEntity webhook;
  protected final EntityManager em;
  protected final RealmModel realm;

  public WebhookAdapter(
      KeycloakSession session, RealmModel realm, EntityManager em, WebhookEntity webhook) {
    this.session = session;
    this.realm = realm;
    this.em = em;
    this.webhook = webhook;
  }

  @Override
  public WebhookEntity getEntity() {
    return webhook;
  }

  @Override
  public String getId() {
    return webhook.getId();
  }

  @Override
  public boolean isEnabled() {
    return webhook.isEnabled();
  }

  @Override
  public void setEnabled(boolean enabled) {
    webhook.setEnabled(enabled);
  }

  @Override
  public String getUrl() {
    return webhook.getUrl();
  }

  @Override
  public void setUrl(String url) {
    webhook.setUrl(url);
  }

  @Override
  public String getSecret() {
    return webhook.getSecret();
  }

  @Override
  public void setSecret(String secret) {
    webhook.setSecret(secret);
  }

  @Override
  public String getAlgorithm() {
    return webhook.getAlgorithm();
  }

  @Override
  public void setAlgorithm(String algorithm) {
    webhook.setAlgorithm(algorithm);
  }

  @Override
  public String getAuthType() {
    return webhook.getAuthType();
  }

  @Override
  public void setAuthType(String authType) {
    webhook.setAuthType(authType);
  }

  @Override
  public String getAudience() {
    return webhook.getAudience();
  }

  @Override
  public void setAudience(String audience) {
    webhook.setAudience(audience);
  }

  @Override
  public String getPayloadFormat() {
    return webhook.getPayloadFormat();
  }

  @Override
  public void setPayloadFormat(String payloadFormat) {
    webhook.setPayloadFormat(payloadFormat);
  }

  @Override
  public Map<String, String> getConfig() {
    Map<String, String> config = new HashMap<>();
    webhook.getConfig().forEach(c -> config.put(c.getName(), c.getValue()));
    return config;
  }

  @Override
  public void setConfig(Map<String, String> config) {
    Map<String, String> incoming = config == null ? Map.of() : config;
    // update existing rows in place: Hibernate flushes inserts before deletes, so replacing a
    // row with the same name would violate the (WEBHOOK_ID, NAME) unique key
    webhook.getConfig().removeIf(c -> !incoming.containsKey(c.getName()));
    Map<String, WebhookConfigEntity> existing = new HashMap<>();
    webhook.getConfig().forEach(c -> existing.put(c.getName(), c));
    incoming.forEach(
        (name, value) -> {
          WebhookConfigEntity c = existing.get(name);
          if (c == null) {
            c = new WebhookConfigEntity();
            c.setId(KeycloakModelUtils.generateId());
            c.setWebhook(webhook);
            c.setName(name);
            webhook.getConfig().add(c);
          }
          c.setValue(value);
        });
  }

  @Override
  public RealmModel getRealm() {
    return session.realms().getRealm(webhook.getRealmId());
  }

  @Override
  public UserModel getCreatedBy() {
    return session.users().getUserById(getRealm(), webhook.getCreatedBy());
  }

  @Override
  public Date getCreatedAt() {
    return webhook.getCreatedAt();
  }

  @Override
  public Set<String> getEventTypes() {
    return webhook.getEventTypes();
  }

  @Override
  public void addEventType(String eventType) {
    webhook.getEventTypes().add(eventType);
  }

  @Override
  public void removeEventTypes() {
    webhook.getEventTypes().clear();
  }
}
