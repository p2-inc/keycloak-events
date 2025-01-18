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
  public WebhookModel getWebhook() {
    return new WebhookAdapter(session, realm, em, send.getWebhook());
  }

  @Override
  public WebhookEventModel getEvent() {
    return new WebhookEventAdapter(session, realm, em, send.getEvent());
  }

  @Override
  public Integer getFinalStatus() {
    return send.getFinalStatus();
  }

  @Override
  public Integer getRetries() {
    return send.getRetries();
  }

  @Override
  public Date getSentAt() {
    return send.getSentAt();
  }
}
