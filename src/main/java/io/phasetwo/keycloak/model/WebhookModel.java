package io.phasetwo.keycloak.model;

import java.util.Date;
import java.util.Set;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public interface WebhookModel {

  /** No authentication: the payload is sent without a signature or bearer token. */
  String AUTH_TYPE_NONE = "none";

  /** HMAC signature of the payload sent in the {@code X-Keycloak-Signature} header. */
  String AUTH_TYPE_HMAC = "hmac";

  /**
   * A short-lived JWT signed by the realm's active signing key, sent in the {@code Authorization:
   * Bearer} header and verifiable against the realm's JWKS endpoint.
   */
  String AUTH_TYPE_BEARER = "bearer";

  /** Default JWS algorithm used when {@link #getAuthType()} is {@link #AUTH_TYPE_BEARER}. */
  String DEFAULT_BEARER_ALGORITHM = "RS256";

  /** Default HMAC algorithm used when {@link #getAuthType()} is {@link #AUTH_TYPE_HMAC}. */
  String DEFAULT_HMAC_ALGORITHM = "HmacSHA256";

  String getId();

  boolean isEnabled();

  void setEnabled(boolean enabled);

  String getUrl();

  void setUrl(String url);

  String getSecret();

  void setSecret(String secret);

  String getAlgorithm();

  void setAlgorithm(String algorithm);

  String getAuthType();

  void setAuthType(String authType);

  String getAudience();

  void setAudience(String audience);

  RealmModel getRealm();

  UserModel getCreatedBy();

  Date getCreatedAt();

  Set<String> getEventTypes();

  void addEventType(String eventType);

  void removeEventTypes();
}
