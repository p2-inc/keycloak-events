package io.phasetwo.keycloak.model.jpa;

import io.phasetwo.keycloak.model.WebhookEventModel;
import io.phasetwo.keycloak.model.WebhookModel;
import io.phasetwo.keycloak.model.WebhookSendModel;
import io.phasetwo.keycloak.model.jpa.entity.WebhookSendEntity;
import jakarta.persistence.EntityManager;
import java.util.Date;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.jpa.JpaModel;

public class WebhookSendAdapter implements WebhookSendModel, JpaModel<WebhookSendEntity> {

  protected final KeycloakSession session;
  protected final WebhookSendEntity send;
  protected final EntityManager em;
  protected final RealmModel realm;

  public WebhookSendAdapter(
      KeycloakSession session, RealmModel realm, EntityManager em, WebhookSendEntity send) {
    this.session = session;
    this.realm = realm;
    this.em = em;
    this.send = send;
  }

  @Override
  public WebhookSendEntity getEntity() {
    return send;
  }

  @Override
  public String getId() {
    return send.getId();
  }

  @Override
  public String getEventType() {
    return send.getEventType();
  }

  @Override
  public WebhookModel getWebhook() {
    return new WebhookAdapter(session, realm, em, send.getWebhook());
  }

  @Override
  public WebhookEventModel getEvent() {
    return new WebhookEventAdapter(session, realm, em, send.getEvent());
  }

  @Override
  public Integer getStatus() {
    return send.getStatus();
  }

  @Override
  public void setStatus(Integer status) {
    send.setStatus(status);
  }

  @Override
  public Integer getRetries() {
    return send.getRetries();
  }

  @Override
  public void incrementRetries() {
    int r = 0;
    if (send.getRetries() != null) r = send.getRetries();
    send.setRetries(r + 1);
  }

  @Override
  public Date getSentAt() {
    return send.getSentAt();
  }

  @Override
  public void setSentAt(Date sentAt) {
    send.setSentAt(sentAt);
  }
}
