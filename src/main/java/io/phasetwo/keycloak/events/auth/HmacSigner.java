package io.phasetwo.keycloak.events.auth;

import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Computes the {@code X-Keycloak-Signature} header: the lowercase hex HMAC of the request body,
 * keyed with the webhook's shared secret.
 */
public class HmacSigner {

  private final String algorithm;
  private final SecretKeySpec key;

  /**
   * @param algorithm a {@link Mac} algorithm, e.g. {@code HmacSHA256}
   * @param secret the shared secret
   */
  public HmacSigner(String algorithm, String secret) {
    this.algorithm = algorithm;
    this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm);
  }

  /**
   * Sign data.
   *
   * @param data the exact bytes to sign
   * @return the signature as lowercase hex
   * @throws SignatureException if the algorithm is unavailable or the key is unusable
   */
  public String sign(byte[] data) throws SignatureException {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(data));
    } catch (Exception e) {
      throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
    }
  }
}
