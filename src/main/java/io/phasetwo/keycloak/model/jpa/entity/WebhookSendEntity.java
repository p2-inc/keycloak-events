package io.phasetwo.keycloak.model.jpa.entity;

import jakarta.persistence.*;
import java.util.Date;

@NamedQueries({
  @NamedQuery(
      name = "getWebhookSendsByWebhook",
      query =
          "SELECT w FROM WebhookSendEntity w WHERE w.webhook = :webhook ORDER BY w.sentAt DESC"),
  @NamedQuery(
      name = "getWebhookSendsByEvent",
      query = "SELECT w FROM WebhookSendEntity w WHERE w.event = :event ORDER BY w.sentAt DESC")
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
  protected WebhookEventEntity event;

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

  public WebhookEventEntity getEvent() {
    return event;
  }

  public void setEvent(WebhookEventEntity event) {
    this.event = event;
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