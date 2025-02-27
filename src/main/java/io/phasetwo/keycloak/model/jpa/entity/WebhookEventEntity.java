package io.phasetwo.keycloak.model.jpa.entity;

import io.phasetwo.keycloak.model.KeycloakEventType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@NamedQueries({
  @NamedQuery(
      name = "getWebhookEventByEventId",
      query =
          "SELECT w FROM WebhookEventEntity w WHERE w.realmId = :realmId AND w.eventType = 'USER' AND w.eventId = :id"),
  @NamedQuery(
      name = "getWebhookEventByAdminEventId",
      query =
          "SELECT w FROM WebhookEventEntity w WHERE w.realmId = :realmId AND w.eventType = 'ADMIN' AND w.adminEventId = :id")
})
@Entity
@Table(name = "WEBHOOK_EVENT")
public class WebhookEventEntity {
  @Id
  @Column(name = "ID", nullable = false, length = 36)
  @Access(AccessType.PROPERTY)
  protected String id;

  @Column(name = "REALM_ID", nullable = false)
  protected String realmId;

  @Column(name = "EVENT_TYPE", nullable = false, length = 36)
  @Enumerated(EnumType.STRING)
  private KeycloakEventType eventType;

  @Column(name = "EVENT_ID", nullable = true, length = 36)
  protected String eventId;

  @Column(name = "ADMIN_EVENT_ID", nullable = true, length = 36)
  protected String adminEventId;

  @Column(name = "EVENT_OBJECT", nullable = true)
  @JdbcTypeCode(SqlTypes.JSON)
  protected String eventObject;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRealmId() {
    return realmId;
  }

  public void setRealmId(String realmId) {
    this.realmId = realmId;
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
