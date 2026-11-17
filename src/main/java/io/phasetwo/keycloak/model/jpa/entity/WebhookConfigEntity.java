package io.phasetwo.keycloak.model.jpa.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A name/value config entry of a webhook. Names are unique per webhook. */
@Entity
@Table(
    name = "WEBHOOK_CONFIG",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"WEBHOOK_ID", "NAME"})})
public class WebhookConfigEntity {
  @Id
  @Column(name = "WEBHOOK_CONFIG_ID", length = 36)
  @Access(AccessType.PROPERTY)
  protected String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "WEBHOOK_ID", nullable = false)
  protected WebhookEntity webhook;

  @Column(name = "NAME", nullable = false)
  protected String name;

  @Column(name = "VALUE", length = 4000)
  protected String value;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WebhookConfigEntity)) return false;
    WebhookConfigEntity that = (WebhookConfigEntity) o;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return id == null ? 0 : id.hashCode();
  }
}
