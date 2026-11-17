package io.phasetwo.keycloak.events.format;

import com.google.auto.service.AutoService;
import org.keycloak.models.KeycloakSession;

/** Registers {@link DefaultPayloadFormatter}, the {@code default} payload format. */
@AutoService(WebhookPayloadFormatterFactory.class)
public class DefaultPayloadFormatterFactory implements WebhookPayloadFormatterFactory {

  public static final String PROVIDER_ID = "default";

  // stateless and thread-safe, so one instance serves every session
  private final DefaultPayloadFormatter formatter = new DefaultPayloadFormatter();

  @Override
  public WebhookPayloadFormatter create(KeycloakSession session) {
    return formatter;
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getHelpText() {
    return "The keycloak-events JSON payload (default).";
  }
}
