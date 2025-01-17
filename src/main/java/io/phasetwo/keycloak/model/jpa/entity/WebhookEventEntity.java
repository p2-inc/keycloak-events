package io.phasetwo.keycloak.model.jpa.entity;

import io.phasetwo.keycloak.model.KeycloakEventType;
import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@NamedQueries({
  @NamedQuery(
      name = "getWebhookEventByEventId",
      query = "SELECT w FROM WebhookEventEntity w WHERE w.eventType = 'EVENT' AND w.eventId = :eventId"),
  @NamedQuery(
      name = "getWebhookEventByAdminEventId",
      query = "SELECT w FROM WebhookEventEntity w WHERE w.eventType = 'ADMIN' AND w.adminEventId = :adminEventId")
})
@Entity
@Table(name = "WEBHOOK_EVENT")
public class WebhookEventEntity {
  @Id
  @Column(name = "ID", nullable = false, length = 36)
  @Access(AccessType.PROPERTY)
  protected String id;

  @Column(name = "EVENT_TYPE", nullable = false, length = 36)
  @Enumerated(EnumType.STRING)
  private KeycloakEventType eventType;
  
  @Column(name = "EVENT_ID", nullable = true, length = 36)
  protected String eventId;

  @Column(name = "ADMIN_EVENT_ID", nullable = true, length = 36)
  protected String adminEventId;

  @Column(name = "EVENT_OBJECT", nullable = true)
  protected String eventObject;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public KeycloakEventType getEventType() {
    return this.eventType;
  }

  public void setEventType(KeycloakEventType eventType) {
    this.eventType = eventType;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getAdminEventId() {
    return adminEventId;
  }

  public void setAdminEventId(String adminEventId) {
    this.adminEventId = adminEventId;
  }

  public String getEventObject() {
    return eventObject;
  }

  public void setEventObject(String eventObject) {
    this.eventObject = eventObject;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!(o instanceof WebhookEntity)) return false;

    WebhookEntity that = (WebhookEntity) o;

    if (!id.equals(that.id)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
