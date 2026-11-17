package io.phasetwo.keycloak.events.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class WebhookAuthConfigTest {

  @Test
  public void testHeadersExtractsPrefixedEntries() {
    Map<String, String> headers =
        WebhookAuthConfig.headers(
            Map.of("header.DD-API-KEY", "k", "header.", "ignored", "service", "keycloak"));
    assertThat(headers, is(Map.of("DD-API-KEY", "k")));
  }

  @Test
  public void testSecretKeys() {
    assertThat(WebhookAuthConfig.isSecretKey("header.x-chronicle-auth"), is(true));
    assertThat(WebhookAuthConfig.isSecretKey("oauth2.clientSecret"), is(true));
    assertThat(WebhookAuthConfig.isSecretKey("oauth2.clientId"), is(false));
    assertThat(WebhookAuthConfig.isSecretKey("service"), is(false));
  }

  @Test
  public void testValidateHeaders() {
    assertThrows(
        IllegalArgumentException.class, () -> WebhookAuthConfig.validate("headers", Map.of()));
    assertDoesNotThrow(() -> WebhookAuthConfig.validate("headers", Map.of("header.X-Key", "v")));
  }

  @Test
  public void testValidateOAuth2() {
    Map<String, String> ok =
        Map.of(
            "oauth2.tokenUrl", "https://login.example.com/token",
            "oauth2.clientId", "c",
            "oauth2.clientSecret", "s");
    assertDoesNotThrow(() -> WebhookAuthConfig.validate("oauth2-client-credentials", ok));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            WebhookAuthConfig.validate(
                "oauth2-client-credentials",
                Map.of("oauth2.tokenUrl", "https://x/token", "oauth2.clientId", "c")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            WebhookAuthConfig.validate(
                "oauth2-client-credentials",
                Map.of(
                    "oauth2.tokenUrl", "not a url",
                    "oauth2.clientId", "c",
                    "oauth2.clientSecret", "s")));
  }

  @Test
  public void testOtherAuthTypesNeedNoConfig() {
    assertDoesNotThrow(() -> WebhookAuthConfig.validate("hmac", null));
    assertDoesNotThrow(() -> WebhookAuthConfig.validate("none", Map.of()));
  }
}
