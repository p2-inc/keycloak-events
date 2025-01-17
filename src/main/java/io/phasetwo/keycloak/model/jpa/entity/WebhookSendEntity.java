package io.phasetwo.keycloak.model.jpa.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@NamedQueries({
  @NamedQuery(
      name = "getWebhookSendsByWebhookId",
      query = "SELECT w FROM WebhookEntity w WHERE w.realmId = :realmId"),
  @NamedQuery(
      name = "countWebhooksByRealmId",
      query = "SELECT count(w) FROM WebhookEntity w WHERE w.realmId = :realmId"),
  @NamedQuery(
      name = "removeAllWebhooks",
      query = "DELETE FROM WebhookEntity w WHERE w.realmId = :realmId")
})
@Entity
@Table(name = "WEBHOOK")
public class WebhookSendEntity {
  @Id
  @Column(name = "ID", length = 36)
  @Access(AccessType.PROPERTY)
  protected String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "WEBHOOK_ID")
  protected WebhookEntity webhook;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "WEBHOOK_EVENT_ID")
  protected WebhookEventEntity webhookEvent;

  @Column(name = "FINAL_STATUS")
  protected Integer finalStatus;

  @Column(name = "RETRIES")
  protected Integer retries;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "SENT_AT")
  protected Date sentAt;

  @PrePersist
  protected void onCreate() {
    if (sentAt == null) sentAt = new Date();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public WebhookEntity getWebhook() {
    return webhook;
  }

  public void setWebhook(WebhookEntity webhook) {
    this.webhook = webhook;
  }

  public WebhookEventEntity getWebhookEvent() {
    return webhookEvent;
  }

  public void setWebhookEvent(WebhookEventEntity webhookEvent) {
    this.webhookEvent = webhookEvent;
  }

  public Integer getFinalStatus() {
    return finalStatus;
  }

  public void setFinalStatus(Integer finalStatus) {
    this.finalStatus = finalStatus;
  }

  public Integer getRetries() {
    return retries;
  }

  public void setRetries(Integer retries) {
    this.retries = retries;
  }

  public Date getSentAt() {
    return sentAt;
  }

  public void setSentAt(Date at) {
    sentAt = at;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!(o instanceof WebhookSendEntity)) return false;

    WebhookSendEntity that = (WebhookSendEntity) o;

    if (!id.equals(that.id)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
