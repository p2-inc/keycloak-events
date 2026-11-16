package io.phasetwo.keycloak.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class WebhookConfigMergeTest {

  static final String MASK = "**********";

  @Test
  public void testNullIncomingKeepsStored() {
    assertThat(
        WebhooksResource.mergeConfig(Map.of("a", "1"), null), is(Map.<String, String>of("a", "1")));
  }

  @Test
  public void testIncomingReplacesStored() {
    assertThat(
        WebhooksResource.mergeConfig(Map.of("a", "1", "b", "2"), Map.of("a", "3")),
        is(Map.<String, String>of("a", "3")));
  }

  @Test
  public void testMaskedValueKeepsStoredSecret() {
    assertThat(
        WebhooksResource.mergeConfig(
            Map.of("header.K", "secret", "x", "1"), Map.of("header.K", MASK, "x", "2")),
        is(Map.<String, String>of("header.K", "secret", "x", "2")));
  }

  @Test
  public void testMaskedValueWithoutStoredSecretIsDropped() {
    assertThat(
        WebhooksResource.mergeConfig(Map.of(), Map.of("header.K", MASK)),
        is(Map.<String, String>of()));
  }
}
