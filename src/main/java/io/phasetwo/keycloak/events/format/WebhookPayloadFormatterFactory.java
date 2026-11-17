package io.phasetwo.keycloak.events.format;

import io.phasetwo.keycloak.PublicAPI;
import java.util.List;
import java.util.Map;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ConfiguredProvider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderFactory;

/**
 * Factory for {@link WebhookPayloadFormatter}s. Register implementations with {@code
 * @AutoService(WebhookPayloadFormatterFactory.class)}.
 *
 * <p>The factory {@link #getId() id} is the value a webhook's {@code payloadFormat} refers to.
 * {@link #getConfigProperties()} describes the per-webhook options the formatter reads; properties
 * marked {@link ProviderConfigProperty#isSecret() secret} are never returned by the webhook REST
 * API.
 */
@PublicAPI(unstable = true)
public interface WebhookPayloadFormatterFactory
    extends ProviderFactory<WebhookPayloadFormatter>, ConfiguredProvider {

  /**
   * Validate a webhook's configuration for this formatter.
   *
   * @throws IllegalArgumentException with a user-facing message if the configuration is invalid
   */
  default void validateConfig(Map<String, String> config) {}

  /**
   * Whether a config key holds a secret, which the webhook REST API masks.
   *
   * @param key a config key
   * @return true if one of {@link #getConfigProperties()} with that name is secret
   */
  default boolean isSecretConfig(String key) {
    return getConfigProperties().stream().anyMatch(p -> p.isSecret() && p.getName().equals(key));
  }

  @Override
  default List<ProviderConfigProperty> getConfigProperties() {
    return List.of();
  }

  @Override
  default String getHelpText() {
    return getId();
  }

  @Override
  default void init(Config.Scope config) {}

  @Override
  default void postInit(KeycloakSessionFactory factory) {}

  @Override
  default void close() {}
}
