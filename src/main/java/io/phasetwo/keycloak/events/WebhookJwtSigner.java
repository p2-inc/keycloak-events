package io.phasetwo.keycloak.events;

import com.google.common.base.Strings;
import io.phasetwo.keycloak.model.WebhookModel;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.SignatureProvider;
import org.keycloak.crypto.SignatureSignerContext;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.services.Urls;

/**
 * Mints a short-lived JWT signed with the realm's active signing key so a webhook receiver can
 * authenticate the payload against the realm's JWKS endpoint (issuer + audience) without a shared
 * secret.
 *
 * <p>The token carries a {@code request_body_sha256} claim binding it to the exact payload, so it
 * cannot be replayed against a different body.
 */
@JBossLog
public final class WebhookJwtSigner {

  /** Realm attribute holding an explicitly configured frontend URL. */
  static final String FRONTEND_URL_ATTRIBUTE = "frontendUrl";

  /** Environment variable holding the Keycloak hostname/base URL. */
  static final String KC_HOSTNAME_ENV = "KC_HOSTNAME";

  /** Claim binding the token to the exact request body. */
  static final String BODY_HASH_CLAIM = "request_body_sha256";

  /** Default token lifespan in seconds; overridable via {@code WEBHOOK_JWT_LIFESPAN_SECONDS}. */
  static final int DEFAULT_LIFESPAN_SECONDS = 300;

  private static final String LIFESPAN_ENV = "WEBHOOK_JWT_LIFESPAN_SECONDS";

  private WebhookJwtSigner() {}

  /**
   * Sign a bearer JWT for the given realm and payload.
   *
   * @param session a live session (needed for realm signing keys)
   * @param realm the realm whose active key signs the token and whose issuer it carries
   * @param algorithm the JWS algorithm (e.g. {@code RS256}); defaults to {@link
   *     WebhookModel#DEFAULT_BEARER_ALGORITHM} when blank
   * @param audience the {@code aud} claim; may be null
   * @param body the exact serialized request body, hashed into the {@code request_body_sha256} claim
   * @param fallbackBaseUri base URI to derive the issuer from when neither the realm frontend URL
   *     nor {@code KC_HOSTNAME} is set; may be null
   * @return the signed compact JWS, or null if it could not be produced
   */
  public static String sign(
      KeycloakSession session,
      RealmModel realm,
      String algorithm,
      String audience,
      String body,
      String fallbackBaseUri) {
    try {
      String alg =
          Strings.isNullOrEmpty(algorithm) ? WebhookModel.DEFAULT_BEARER_ALGORITHM : algorithm;

      KeyWrapper key = session.keys().getActiveKey(realm, KeyUse.SIG, alg);
      if (key == null) {
        log.warnf(
            "No active %s signing key for realm %s; cannot sign webhook JWT", alg, realm.getName());
        return null;
      }
      SignatureSignerContext signer = session.getProvider(SignatureProvider.class, alg).signer(key);

      int now = Time.currentTime();
      JsonWebToken token = new JsonWebToken();
      token.id(KeycloakModelUtils.generateId());
      token.issuer(resolveIssuer(realm, fallbackBaseUri));
      if (!Strings.isNullOrEmpty(audience)) {
        token.audience(audience);
      }
      token.iat((long) now);
      token.exp((long) (now + lifespanSeconds()));
      token.setOtherClaims(BODY_HASH_CLAIM, sha256Hex(body));

      return new JWSBuilder().type("JWT").kid(key.getKid()).jsonContent(token).sign(signer);
    } catch (Exception e) {
      log.warn("Unable to sign webhook JWT", e);
      return null;
    }
  }

  /**
   * Resolve the token issuer the same way Keycloak resolves realm token issuers: prefer the realm's
   * configured frontend URL, then {@code KC_HOSTNAME}, then the supplied request base URI.
   */
  static String resolveIssuer(RealmModel realm, String fallbackBaseUri) {
    String base = realm.getAttribute(FRONTEND_URL_ATTRIBUTE);
    if (Strings.isNullOrEmpty(base)) {
      base = System.getenv(KC_HOSTNAME_ENV);
    }
    if (Strings.isNullOrEmpty(base)) {
      base = fallbackBaseUri;
    }
    if (Strings.isNullOrEmpty(base)) {
      // last resort: an opaque but stable issuer derived from the realm name
      return Urls.realmIssuer(URI.create("https://localhost/"), realm.getName());
    }
    if (!base.startsWith("http://") && !base.startsWith("https://")) {
      base = "https://" + base;
    }
    if (!base.endsWith("/")) {
      base = base + "/";
    }
    return Urls.realmIssuer(URI.create(base), realm.getName());
  }

  private static int lifespanSeconds() {
    String v = System.getenv(LIFESPAN_ENV);
    if (!Strings.isNullOrEmpty(v)) {
      try {
        int parsed = Integer.parseInt(v.trim());
        if (parsed > 0) return parsed;
      } catch (NumberFormatException e) {
        log.warnf("Invalid %s=%s; using default %d", LIFESPAN_ENV, v, DEFAULT_LIFESPAN_SECONDS);
      }
    }
    return DEFAULT_LIFESPAN_SECONDS;
  }

  static String sha256Hex(String data) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      String s = Integer.toHexString(0xFF & b);
      if (s.length() == 1) sb.append('0');
      sb.append(s);
    }
    return sb.toString();
  }
}
