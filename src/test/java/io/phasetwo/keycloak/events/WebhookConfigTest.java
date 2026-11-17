package io.phasetwo.keycloak.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.phasetwo.keycloak.events.auth.OAuth2ClientCredentials;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class WebhookConfigTest {

  static final String MASK = "**********";

  static final Map<String, String> OAUTH2 =
      Map.of(
          "oauth2.tokenUrl", "https://login.example.com/token",
          "oauth2.clientId", "c",
          "oauth2.clientSecret", "s");

  @Test
  public void testHeadersExtractsPrefixedEntries() {
    WebhookConfig config =
        new WebhookConfig(
            Map.of("header.DD-API-KEY", "k", "header.", "ignored", "service", "keycloak"));
    assertThat(config.headers(), is(Map.of("DD-API-KEY", "k")));
  }

  @Test
  public void testNullEntriesAreDropped() {
    Map<String, String> values = new HashMap<>();
    values.put("a", null);
    values.put("b", "1");
    assertThat(new WebhookConfig(values).asMap(), is(Map.of("b", "1")));
    assertThat(new WebhookConfig(null).asMap(), is(Map.of()));
  }

  @Test
  public void testSecretKeys() {
    WebhookConfig config = new WebhookConfig(Map.of());
    assertThat(config.isSecret("header.x-chronicle-auth"), is(true));
    assertThat(config.isSecret("oauth2.clientSecret"), is(true));
    assertThat(config.isSecret("oauth2.clientId"), is(false));
    assertThat(config.isSecret("service"), is(false));
    assertThat(config.isSecret(null), is(false));
  }

  @Test
  public void testMaskedHidesAuthAndFormatSecrets() {
    WebhookConfig config =
        new WebhookConfig(Map.of("header.K", "k", "apiToken", "t", "service", "keycloak"));
    assertThat(
        config.masked("apiToken"::equals),
        is(Map.of("header.K", MASK, "apiToken", MASK, "service", "keycloak")));
  }

  @Test
  public void testValidateHeaders() {
    assertThrows(
        IllegalArgumentException.class, () -> new WebhookConfig(Map.of()).validateFor("headers"));
    assertDoesNotThrow(() -> new WebhookConfig(Map.of("header.X-Key", "v")).validateFor("headers"));
  }

  @Test
  public void testValidateOAuth2() {
    assertDoesNotThrow(() -> new WebhookConfig(OAUTH2).validateFor("oauth2-client-credentials"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WebhookConfig(Map.of("oauth2.tokenUrl", "https://x/token", "oauth2.clientId", "c"))
                .validateFor("oauth2-client-credentials"));
    Map<String, String> badUrl = new HashMap<>(OAUTH2);
    badUrl.put("oauth2.tokenUrl", "not a url");
    assertThrows(
        IllegalArgumentException.class,
        () -> new WebhookConfig(badUrl).validateFor("oauth2-client-credentials"));
  }

  @Test
  public void testOtherAuthTypesNeedNoConfig() {
    assertDoesNotThrow(() -> new WebhookConfig(null).validateFor("hmac"));
    assertDoesNotThrow(() -> new WebhookConfig(Map.of()).validateFor("none"));
  }

  @Test
  public void testOAuth2ClientCredentials() {
    OAuth2ClientCredentials creds = new WebhookConfig(OAUTH2).oauth2ClientCredentials();
    assertThat(
        creds, is(new OAuth2ClientCredentials("https://login.example.com/token", "c", "s", null)));
    // the secret never shows up in logs
    assertThat(creds.toString(), not(containsString("s]")));
    assertThat(new WebhookConfig(OAUTH2).toString(), not(containsString("login.example.com")));
  }

  @Test
  public void testMergeNullKeepsStored() {
    WebhookConfig stored = new WebhookConfig(Map.of("a", "1"));
    assertThat(stored.merge(null), is(stored));
  }

  @Test
  public void testMergeReplacesStored() {
    assertThat(
        new WebhookConfig(Map.of("a", "1", "b", "2")).merge(Map.of("a", "3")).asMap(),
        is(Map.of("a", "3")));
  }

  @Test
  public void testMergeMaskedValueKeepsStoredSecret() {
    assertThat(
        new WebhookConfig(Map.of("header.K", "secret", "x", "1"))
            .merge(Map.of("header.K", MASK, "x", "2"))
            .asMap(),
        is(Map.of("header.K", "secret", "x", "2")));
  }

  @Test
  public void testMergeMaskedValueWithoutStoredSecretIsDropped() {
    assertThat(new WebhookConfig(Map.of()).merge(Map.of("header.K", MASK)).asMap(), is(Map.of()));
  }
}
