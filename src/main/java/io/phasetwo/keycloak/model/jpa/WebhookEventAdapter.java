package io.phasetwo.keycloak.model.jpa;

import io.phasetwo.keycloak.model.KeycloakEventType;
import io.phasetwo.keycloak.model.WebhookEventModel;
import io.phasetwo.keycloak.model.jpa.entity.WebhookEventEntity;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.jpa.JpaModel;
import org.keycloak.util.JsonSerialization;

public class WebhookEventAdapter implements WebhookEventModel, JpaModel<WebhookEventEntity> {

  protected final KeycloakSession session;
  protected final WebhookEventEntity event;
  protected final EntityManager em;
  protected final RealmModel realm;

  public WebhookEventAdapter(
      KeycloakSession session, RealmModel realm, EntityManager em, WebhookEventEntity event) {
    this.session = session;
    this.realm = realm;
    this.em = em;
    this.event = event;
  }

  @Override
  public WebhookEventEntity getEntity() {
    return event;
  }

  @Override
  public String getId() {
    return event.getId();
  }

  @Override
  public RealmModel getRealm() {
    return session.realms().getRealm(event.getRealmId());
  }

  @Override
  public KeycloakEventType getEventType() {
    return event.getEventType();
  }

  @Override
  public String getEventId() {
    return event.getEventId();
  }

  @Override
  public String getAdminEventId() {
    return event.getAdminEventId();
  }

  @Override
  public String rawPayload() {
    return event.getEventObject();
  }

  @Override
  public <T> T getPayload(Class<T> clazz) throws IOException {
    return JsonSerialization.readValue(event.getEventObject(), clazz);
  }
}
