package io.phasetwo.keycloak.model.jpa;

import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.jpa.entity.WebhookEntity;
import java.util.Date;
import java.util.Set;
import javax.persistence.EntityManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.jpa.JpaModel;

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
