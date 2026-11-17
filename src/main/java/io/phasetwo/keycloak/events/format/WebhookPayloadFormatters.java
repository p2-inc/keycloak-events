package io.phasetwo.keycloak.events.format;

import com.google.common.base.Strings;
import io.phasetwo.keycloak.events.auth.WebhookAuthConfig;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderFactory;

/** Lookup and validation helpers for {@link WebhookPayloadFormatter}s. */
public final class WebhookPayloadFormatters {

  /** Placeholder returned instead of secret config values. */
  public static final String SECRET_MASK = "**********";

  private WebhookPayloadFormatters() {}

  /**
   * Resolve the formatter for a webhook. A blank id selects the server-wide default (the SPI's
   * configured default provider, falling back to {@link DefaultPayloadFormatter}).
   *
   * @return the formatter, or null if {@code id} names a formatter that isn't installed
   */
  public static WebhookPayloadFormatter resolve(KeycloakSession session, String id) {
    if (Strings.isNullOrEmpty(id)) {
      WebhookPayloadFormatter f = session.getProvider(WebhookPayloadFormatter.class);
      return f != null ? f : DefaultPayloadFormatter.INSTANCE;
    }
    return session.getProvider(WebhookPayloadFormatter.class, id);
  }

  public static WebhookPayloadFormatterFactory getFactory(
      KeycloakSessionFactory sessionFactory, String id) {
    return (WebhookPayloadFormatterFactory)
        sessionFactory.getProviderFactory(WebhookPayloadFormatter.class, id);
  }

  public static Set<WebhookPayloadFormatterFactory> getFactories(
      KeycloakSessionFactory sessionFactory) {
    return sessionFactory
        .getProviderFactoriesStream(WebhookPayloadFormatter.class)
        .map(f -> (WebhookPayloadFormatterFactory) f)
        .collect(Collectors.toSet());
  }

  /** Validate a format id and its config. Throws IllegalArgumentException when invalid. */
  public static void validate(
      KeycloakSessionFactory sessionFactory, String id, Map<String, String> config) {
    if (Strings.isNullOrEmpty(id)) return;
    WebhookPayloadFormatterFactory factory = getFactory(sessionFactory, id);
    if (factory == null) {
      throw new IllegalArgumentException(String.format("unknown payloadFormat %s", id));
    }
    factory.validateConfig(config == null ? Map.of() : config);
  }

  /** The config keys whose values must never be returned by the API. */
  public static boolean isSecretKey(
      KeycloakSessionFactory sessionFactory, String formatId, String key) {
    if (WebhookAuthConfig.isSecretKey(key)) return true;
    return secretKeys(sessionFactory, formatId).contains(key);
  }

  static Set<String> secretKeys(KeycloakSessionFactory sessionFactory, String formatId) {
    Set<String> keys = new HashSet<>();
    if (Strings.isNullOrEmpty(formatId)) return keys;
    ProviderFactory<?> f = getFactory(sessionFactory, formatId);
    if (f instanceof WebhookPayloadFormatterFactory wf) {
      for (ProviderConfigProperty p : wf.getConfigProperties()) {
        if (p.isSecret()) keys.add(p.getName());
      }
    }
    return keys;
  }
}
