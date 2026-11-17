package io.phasetwo.keycloak.events.auth;

import com.google.common.base.Strings;
import java.net.URI;

/**
 * The settings for an OAuth2 client credentials grant. Two webhooks with the same credentials share
 * a cached token.
 *
 * @param tokenUrl the token endpoint
 * @param clientId the client id
 * @param clientSecret the client secret
 * @param scope the requested scope, or null for none
 */
public record OAuth2ClientCredentials(
    String tokenUrl, String clientId, String clientSecret, String scope) {

  /**
   * Check that the grant can be attempted.
   *
   * @throws IllegalArgumentException with a user-facing message naming the missing or invalid
   *     setting
   */
  public void validate() {
    require(tokenUrl, "tokenUrl");
    require(clientId, "clientId");
    require(clientSecret, "clientSecret");
    try {
      new URI(tokenUrl).parseServerAuthority();
    } catch (Exception e) {
      throw new IllegalArgumentException("oauth2.tokenUrl is not a URL");
    }
  }

  private void require(String value, String name) {
    if (Strings.isNullOrEmpty(value)) {
      throw new IllegalArgumentException("oauth2 client credentials require oauth2." + name);
    }
  }

  /** Never includes the secret, so credentials can't leak into logs. */
  @Override
  public String toString() {
    return String.format(
        "OAuth2ClientCredentials[tokenUrl=%s, clientId=%s, scope=%s]", tokenUrl, clientId, scope);
  }
}
