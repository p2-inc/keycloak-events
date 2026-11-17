package io.phasetwo.keycloak.events.format;

import com.google.common.base.Strings;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/** The payload formats installed in a Keycloak server, looked up through the formatter SPI. */
public class PayloadFormats {

  private final KeycloakSession session;

  public PayloadFormats(KeycloakSession session) {
    this.session = session;
  }

  private KeycloakSessionFactory sessionFactory() {
    return session.getKeycloakSessionFactory();
  }

  /**
   * The formatter for a webhook's {@code payloadFormat}. A blank id selects the server-wide default
   * (the SPI's configured default provider, falling back to {@link DefaultPayloadFormatter}).
   *
   * @param id a formatter id, or null/blank for the default
   * @return the formatter, or empty if {@code id} names a formatter that isn't installed
   */
  public Optional<WebhookPayloadFormatter> formatter(String id) {
    if (Strings.isNullOrEmpty(id)) {
      WebhookPayloadFormatter f = session.getProvider(WebhookPayloadFormatter.class);
      return Optional.of(f != null ? f : new DefaultPayloadFormatter());
    }
    return Optional.ofNullable(session.getProvider(WebhookPayloadFormatter.class, id));
  }

  /**
   * The factory for a formatter id.
   *
   * @param id a formatter id
   * @return the factory, or empty if no formatter with that id is installed or the id is blank
   */
  public Optional<WebhookPayloadFormatterFactory> factory(String id) {
    if (Strings.isNullOrEmpty(id)) return Optional.empty();
    return Optional.ofNullable(
        (WebhookPayloadFormatterFactory)
            sessionFactory().getProviderFactory(WebhookPayloadFormatter.class, id));
  }

  /**
   * All installed formatter factories.
   *
   * @return the factories
   */
  public Stream<WebhookPayloadFormatterFactory> factories() {
    return sessionFactory()
        .getProviderFactoriesStream(WebhookPayloadFormatter.class)
        .map(WebhookPayloadFormatterFactory.class::cast);
  }

  /**
   * Validate a format id and its config. A blank id (the server default) is always valid.
   *
   * @param id a formatter id, or null/blank for the default
   * @param config the webhook config
   * @throws IllegalArgumentException with a user-facing message if either is invalid
   */
  public void validate(String id, Map<String, String> config) {
    if (Strings.isNullOrEmpty(id)) return;
    factory(id)
        .orElseThrow(() -> new IllegalArgumentException("unknown payloadFormat " + id))
        .validateConfig(config == null ? Map.of() : config);
  }

  /**
   * Whether a config key holds a secret of the given format, i.e. a {@link
   * org.keycloak.provider.ProviderConfigProperty#isSecret() secret} config property.
   *
   * @param formatId a formatter id, or null/blank for the default
   * @param key a config key
   * @return true if the format declares the key secret
   */
  public boolean isSecret(String formatId, String key) {
    return factory(formatId).map(f -> f.isSecretConfig(key)).orElse(false);
  }
}
