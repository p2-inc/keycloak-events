package io.phasetwo.keycloak.model.jpa.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@NamedQueries({
  @NamedQuery(
      name = "getWebhooksByRealmId",
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
public class WebhookEntity {
  @Id
  @Column(name = "ID", length = 36)
  @Access(AccessType.PROPERTY)
  protected String id;

  @Column(name = "ENABLED", nullable = false)
  protected boolean enabled;

  @Column(name = "REALM_ID", nullable = false)
  protected String realmId;

  @Column(name = "URL", nullable = false)
  protected String url;

  @Column(name = "SECRET")
  protected String secret;

  @Column(name = "ALGORITHM")
  protected String algorithm;

  @Column(name = "AUTH_TYPE")
  protected String authType;

  @Column(name = "AUDIENCE")
  protected String audience;

  @ElementCollection(fetch = FetchType.EAGER)
  @Column(name = "VALUE")
  @CollectionTable(
      name = "WEBHOOK_EVENT_TYPES",
      joinColumns = {@JoinColumn(name = "WEBHOOK_ID")})
  protected Set<String> eventTypes = new HashSet<>();

  @Column(name = "CREATED_BY_USER_ID")
  protected String createdBy;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATED_AT")
  protected Date createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) createdAt = new Date();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public boolean isEnabled() {
    return this.enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getRealmId() {
    return realmId;
  }

  public void setRealmId(String realmId) {
    this.realmId = realmId;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  public String getAuthType() {
    return authType;
  }

  public void setAuthType(String authType) {
    this.authType = authType;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public Set<String> getEventTypes() {
    return eventTypes;
  }

  public void setEventTypes(Set<String> eventTypes) {
    this.eventTypes = eventTypes;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date at) {
    createdAt = at;
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
