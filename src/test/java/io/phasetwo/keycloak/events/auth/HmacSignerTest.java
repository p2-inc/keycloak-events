package io.phasetwo.keycloak.events.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.phasetwo.keycloak.events.HttpSenderEventListenerProvider;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import org.junit.jupiter.api.Test;

public class HmacSignerTest {

  static final byte[] FOX =
      "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);

  @Test
  public void testKnownVectors() throws Exception {
    assertThat(
        new HmacSigner("HmacSHA256", "key").sign(FOX),
        is("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"));
    assertThat(
        new HmacSigner("HmacSHA1", "key").sign(FOX),
        is("de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9"));
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testMatchesTheDeprecatedStringSigner() throws Exception {
    String data = "événement";
    assertThat(
        new HmacSigner("HmacSHA256", "s3cret").sign(data.getBytes(StandardCharsets.UTF_8)),
        is(HttpSenderEventListenerProvider.calculateHmacSha(data, "s3cret", "HmacSHA256")));
  }

  @Test
  public void testUnknownAlgorithm() {
    assertThrows(SignatureException.class, () -> new HmacSigner("HmacNope", "k").sign(FOX));
  }
}
